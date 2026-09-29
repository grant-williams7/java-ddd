# 8. Idempotent commands

Your client sends `POST /products`. The server creates the product, then the load balancer kills the connection before the response gets out. The client sees a timeout. What does every reasonable HTTP client do on a timeout? It retries. Now you have two products.

This is not an exotic failure mode — it's Tuesday. Mobile clients on flaky networks, rolling deploys, proxy timeouts: anything separating "the server did the work" from "the client learned about it" will eventually duplicate your writes. The standard fix is an **idempotency key**: the client generates a unique key per logical operation, sends it with the request, and the server guarantees the same key never executes the operation twice.

The concept is simple. The implementation is where I got burned, so this chapter walks the version the template actually ships — including the parts I got wrong the first time.

## The naive version (which I shipped)

```java
Optional<IdempotencyRecord> existing = idempotencyRepository.findByKey(command.idempotencyKey());
if (existing.isPresent()) {
    return decodeCachedResponse(existing.get());
}

CreateProductCommandResult result = doCreateProduct(command);

idempotencyRepository.store(command.idempotencyKey(), result);
return result;
```

Check, execute, store. It reads correctly and passes every test you'll write for it, because sequential tests can't see the bug.

The bug: two requests with the same key arrive *at the same time*. Both call `findByKey`, both get nothing (storing happens after execution), both execute. Two products, one idempotency key — the mechanism silently did nothing exactly when it mattered. And concurrent duplicates are the **main** case, not a corner case: retries fire because something is slow, and when something is slow, the original request is usually still running.

## Fix one: reserve the key atomically

The check and the claim must be one atomic operation. In Postgres that's a unique constraint plus `ON CONFLICT DO NOTHING` ([`JdbcIdempotencyRepository`](https://github.com/<owner>/java-ddd/blob/main/src/main/java/com/example/marketplace/infrastructure/db/postgres/JdbcIdempotencyRepository.java)):

```java
/** Atomically claims the key. Zero rows means another request already holds it. */
private static final String RESERVE_IDEMPOTENCY_KEY = """
        INSERT INTO idempotency_records (id, key, request, response, status_code, created_at)
        VALUES (:id, :key, :request, '', 0, :created_at)
        ON CONFLICT (key) DO NOTHING""";
```

`JdbcClient.update()` returns the affected-row count, and that count is the whole trick: **1 means you won the race and may execute; 0 means someone else holds the key and you must not.** The row is inserted *before* execution as a reservation (`response = ''`, `status_code = 0` meaning "in flight"), not after execution as a cache entry. Postgres's unique index is the arbiter of who executes — no advisory locks, no Redis, no distributed-lock library. The guarantee comes from the database, which is exactly where I want it.

## The losers need answers too

If you lost the race, what do you tell the client? Depends on the winner ([`Idempotency.java`](https://github.com/<owner>/java-ddd/blob/main/src/main/java/com/example/marketplace/application/services/Idempotency.java)):

```java
boolean reserved = false;
for (int attempt = 0; attempt < MAX_ATTEMPTS && !reserved; attempt++) {
    reserved = repository.reserve(record);
    if (reserved) {
        break;
    }

    Optional<IdempotencyRecord> found = repository.findByKey(key);
    if (found.isEmpty()) {
        // Released between reserve and findByKey; try again.
        continue;
    }
    IdempotencyRecord existing = found.get();

    if (!existing.getRequest().equals(request)) {
        throw new IdempotencyKeyReuseException();
    }

    if (existing.isCompleted()) {
        return decodeCachedResponse(existing.getResponse(), resultType);
    }

    if (Duration.between(existing.getCreatedAt(), Timestamps.now()).compareTo(RESERVATION_TTL) < 0) {
        throw new RequestInFlightException();
    }

    // Stale reservation: the previous holder crashed before completing.
    // Release it and retry the reservation.
    repository.delete(key);
}
```

Four outcomes, each deliberate:

- **Winner finished** → serve its stored response. The retry gets the same `201` body the original would have gotten, byte for byte.
- **Winner still running** → `RequestInFlightException`, mapped to **409 Conflict**. Back off, retry, hit the cached response.
- **Same key, different payload** → `IdempotencyKeyReuseException`, mapped to **422**. Easy to skip, dangerous to skip: if a client bug reuses a key across different requests, silently returning request A's cached response to request B means the caller thinks it created B while holding A. Fail loudly.
- **Reservation older than the TTL with no response** → the holder is dead; take over (next section).

Generics make this one decorator for every command — `withIdempotency` wraps `createProduct`, `updateSeller`, and friends identically, which is why [chapter 6's](06-cqrs.md) service body starts with it:

```java
<T> T withIdempotency(String key, Object command, Class<T> resultType, Supplier<T> action)
```

The `Class<T>` parameter is Java's tax for type erasure: at runtime, `T` is gone, so decoding a cached response back into a `CreateProductCommandResult` needs the class passed in explicitly. The application layer never touches Jackson, either — a small `ResultCodec` port does the encoding, and infrastructure implements it.

## Fix two: crashes must not brick a key

Reservation-before-execution creates a new failure mode: reserve, then die — OOM kill, deploy — before storing a response. The row says "in flight" forever; every retry gets 409 until a human deletes it. You've traded duplicate writes for a permanently wedged operation.

That's the `RESERVATION_TTL` branch above. A reservation past the TTL with no response means the holder is dead; the next retry deletes the stale row and re-reserves. Because re-reserving is the same atomic `INSERT ... ON CONFLICT`, two retries racing to take over still resolve to one winner.

Sizing the TTL is a judgment call: comfortably longer than your slowest *legitimate* execution, or you'll take over reservations that are merely slow — and be back to duplicates. A minute is generous for CRUD; a batch job needs a different mechanism.

## Fix three: release on failure — so that the cleanup survives the failure

If execution fails, release the reservation so the client's retry can actually run. Easy. Except: *why* did execution fail? Sometimes because the request was cancelled — a timeout or a shutdown interrupted the thread doing the work. An interrupted thread is exactly the one that can't talk to the database anymore: HikariCP refuses to hand an interrupted thread a connection. Call `repository.delete(key)` in that state and the DELETE never reaches Postgres — the reservation leaks, and the client whose request was cut off (the one most likely to retry!) is locked out for a full TTL. The cleanup fails precisely in the scenario it exists for.

The rule is that cleanup must not share the failed work's fate. So the release clears the thread's interrupt flag for its own database call and puts it back afterwards:

```java
T result;
try {
    result = action.get();
} catch (RuntimeException | Error failure) {
    release(key);
    throw failure;
}

storeResponse(key, result);
```

```java
private void release(String key) {
    runUninterrupted(() -> {
        try {
            repository.delete(key);
        } catch (RuntimeException e) {
            log.warn("failed to release idempotency key idempotency_key={} error={}", key, e.toString());
        }
    });
}

private static void runUninterrupted(Runnable work) {
    boolean interrupted = Thread.interrupted();
    try {
        work.run();
    } finally {
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
```

There's a second way to fall into the same trap in Spring: transactions. If your services are `@Transactional`, the failure marks the transaction rollback-only, and a release that runs inside it is rolled back along with the failed work. The release then needs its own transaction (`Propagation.REQUIRES_NEW`). This template's services aren't transactional — each repository owns its transaction — so the release is simply its own statement.

Same reasoning for `storeResponse` — the business operation already committed; persisting the cached response must not be at the mercy of the request's fate either. And it's best-effort: failing the whole request because the *cache write* failed would lie to the client about work that's done. Log it; the TTL takeover covers the wedged row.

## Proving it under concurrency

Sequential tests can't catch the original bug, so [the test suite](https://github.com/<owner>/java-ddd/blob/main/src/test/java/com/example/marketplace/application/services/IdempotencyTest.java) fires eight concurrent threads at one key and asserts the only thing that matters:

```java
assertThat(executions).as("exactly one caller may execute").hasValue(1);
assertThat(successes.get() + inFlight.get()).isEqualTo(callers);
```

Successes can exceed one — a loser arriving after the winner finished legitimately gets the cached response — but the business logic runs exactly once, always. Java has no race detector, so the test provokes the race instead: a `CountDownLatch` start gate releases all eight threads at the same instant, and `@RepeatedTest(20)` runs the whole thing twenty times.

Against the live stack, the behavior in four commands:

```bash
for i in $(seq 5); do
  curl -s -o /dev/null -w "%{http_code}\n" -X POST localhost:8080/api/v1/products \
    -H 'Content-Type: application/json' \
    -d '{"idempotency_key":"k-42","name":"Widget","price_minor_units":999,"currency":"EUR","seller_id":"..."}' &
done; wait
```

One `201`, four `409`, one row in the database. Retry a moment later: the winner's `201` body back, byte for byte. Same key with `"name":"Gadget"`: `422`.

## The one-sentence version

Idempotency is a **write-side claim, not a read-side check**. If your implementation reads before it writes, it has the race, full stop — make the database's unique constraint do the deciding and branch on rows-affected. Everything else here (TTL takeover, payload comparison, interrupt-safe cleanup) is consequences of taking that sentence seriously.

## Try it

1. Reproduce the naive bug: comment out the `reserve` call, make the wrapper check-then-store, and run the concurrency test. Watch `executions` climb past 1.
2. Set `RESERVATION_TTL` to `Duration.ofMillis(1)` and rerun the tests. Which ones fail, and what duplicate behavior do they demonstrate? (This is why the TTL must exceed legitimate execution time.)
3. Trace the 409 and 422 from exception to status code in [`RestErrors.java`](https://github.com/<owner>/java-ddd/blob/main/src/main/java/com/example/marketplace/interfaces/api/rest/RestErrors.java) — the same exceptions-by-type pattern from [chapter 5](05-repositories.md), now covering concurrency semantics.

Next: [testing a DDD codebase](09-testing.md) — why these layers make tests fast where they can be and honest where they must be.
