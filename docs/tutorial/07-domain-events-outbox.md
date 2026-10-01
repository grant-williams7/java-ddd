# 7. Domain events and the outbox

Here's a piece of code I've written, in some form, at three different companies:

```java
productRepository.create(product);

// Tell the rest of the world
kafkaTemplate.send("product.created", toEvent(product));
```

Save to Postgres, then publish to Kafka. It works in every demo, every test, and roughly 99.9% of the time in production. The remaining 0.1% is where the fun is: the database and the broker are two separate systems, and **no transaction spans both**.

- Crash after the DB commit, before the publish → the product exists, but the search index, pricing service, and notifications never hear about it. Silent data drift.
- Flip the order — publish first — and a failed insert produces a *ghost event*: consumers react to a product that was never created.

You can't "just be careful" your way out. A retry loop shrinks the window; it doesn't close it. This is the **dual-write problem**, and the fix is old and boring, which is exactly what you want: the **transactional outbox**. Don't publish the event — *store* it, in the same database, in the same transaction as the state change. A separate process does the actual publishing.

## First decision: events belong to the aggregate

Before any infrastructure: who creates the event? In many codebases the service layer builds it, right next to the publish call. That's backwards. "A product was created" is a domain fact, and the aggregate is the thing that knows its own state changed. So the aggregate records events as part of the change:

```java
public static Product create(String name, Money price, ValidatedSeller seller) {
    Instant now = Timestamps.now();
    UUID sellerId = seller.seller().getId();
    Product product = new Product(Uuids.newV7(), now, now, name, price, sellerId, List.of());

    product.recordEvent(ProductCreated.of(
            product.id, name, price.minorUnits(), price.currency().code(), sellerId));

    return product;
}

/**
 * Returns the recorded domain events and clears them. The repository stores
 * them in the same transaction as the product (transactional outbox), so
 * callers pull exactly once per save.
 */
public List<DomainEvent> pullEvents() {
    List<DomainEvent> pulled = List.copyOf(domainEvents);
    domainEvents.clear();
    return pulled;
}
```

The events are dumb records — past-tense names, immutable, no behavior ([`domain/events/`](https://github.com/<owner>/java-ddd/blob/main/src/main/java/com/example/marketplace/domain/events/ProductCreated.java)):

```java
public interface DomainEvent {

    UUID eventId();

    String eventName();

    Instant occurredAt();

    /** The aggregate the event belongs to. */
    UUID aggregateId();
}
```

```java
public record ProductCreated(
        BaseEvent base,
        String name,
        long priceMinorUnits,
        String currency,
        UUID sellerId) implements DomainEvent {

    public static final String NAME = "product.created";

    @Override
    public String eventName() {
        return NAME;
    }
    // ...
}
```

Two details worth noticing. The event Id is a UUIDv7 — time-ordered, so it sorts nicely and doubles as a deduplication key for consumers. And `pullEvents` *clears* the list, so the repository pulls exactly once per save and a retried save can't double-insert the same events.

## One transaction or it didn't happen

The outbox table ([`V3__outbox.sql`](https://github.com/<owner>/java-ddd/blob/main/src/main/resources/db/migration/V3__outbox.sql)) is deliberately simple:

```sql
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_name TEXT NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_outbox_events_unpublished
    ON outbox_events(occurred_at) WHERE published_at IS NULL;
```

Note the **partial index**. The relay only ever asks one question — "unpublished events, oldest first" — while the table grows forever. An index on `WHERE published_at IS NULL` stays tiny no matter how many millions of published rows accumulate, because rows drop out of it the moment they're marked published.

The payoff happens in the repository ([chapter 5](05-repositories.md) showed the transaction): aggregate insert and outbox insert share one JDBC transaction, run by a `TransactionTemplate`.

```java
return Objects.requireNonNull(transactions.execute(status -> {
    jdbc.sql(CREATE_PRODUCT)
            // ...
            .query()
            .singleRow();

    outbox.insert(product.pullEvents());

    return findById(product.getId()).orElseThrow(() ->
            new IllegalStateException("product " + product.getId() + " not readable after insert"));
}));
```

Either the product row *and* its events commit, or neither does. No orphaned product, no ghost event. And the service layer knows nothing about any of this — it calls `productRepository.create` and the events ride along. **You can't forget to publish, because there is no publish step to forget.**

The stored payload is the event as JSON (`{"eventId": …, "aggregateId": …, "occurredAt": …, "name": …, "priceMinorUnits": 4999, "currency": "EUR", "sellerId": …}`). Its shape is defined in infrastructure, by [`OutboxPayloads`](https://github.com/<owner>/java-ddd/blob/main/src/main/java/com/example/marketplace/infrastructure/db/postgres/OutboxPayloads.java), so the domain event records stay free of serialization concerns.

## The relay: dumb on purpose

Something still has to move events from Postgres to the broker. That's the [relay](https://github.com/<owner>/java-ddd/blob/main/src/main/java/com/example/marketplace/infrastructure/outbox/OutboxRelay.java) — a scheduled method that polls unpublished rows and hands them to a `Publisher`:

```java
interface Publisher {

    void publish(String eventName, String payload);
}
```

```java
@Scheduled(initialDelay = 5, fixedRate = 5, timeUnit = TimeUnit.SECONDS)
void relay() {
    try {
        relayBatch();
    } catch (RuntimeException e) {
        log.error("outbox relay batch failed error={}", e.toString());
    }
}

void relayBatch() {
    List<OutboxEvent> events = jdbc.sql(GET_UNPUBLISHED_OUTBOX_EVENTS)
            .param("limit", BATCH_SIZE)
            .query((row, rowNumber) -> new OutboxEvent(
                    row.getObject("id", UUID.class),
                    row.getString("event_name"),
                    row.getString("payload")))
            .list();

    // A failure stops the batch; unpublished events are retried next tick.
    for (OutboxEvent event : events) {
        publisher.publish(event.eventName(), event.payload());
        jdbc.sql(MARK_OUTBOX_EVENT_PUBLISHED).param("id", event.id()).update();
    }
}
```

In the template the publisher just logs via SLF4J; in production you swap in Kafka, NATS, SQS. The interesting property is the failure mode: publish succeeds, then marking the row published fails — crash, network blip, deploy. Next tick, the row is still unpublished, so it publishes **again**.

That's not a bug; it's the contract: the outbox gives you **at-least-once** delivery, never exactly-once. Every consumer must be idempotent — handling `product.created` twice must equal handling it once. The event Id is the dedup key. If that sounds like a burden: you needed idempotent consumers anyway. Kafka redelivers on consumer-group rebalances all by itself. At-least-once is the honest default of distributed messaging; the outbox just stops pretending otherwise.

## The caveats nobody puts in the diagram

**Ordering.** Poll order is `occurred_at`; with a single relay, one aggregate's events come out in recording order. That's per-relay ordering, not global. Publishing to a partitioned topic? Partition by `aggregate_id`. Cross-aggregate ordering is a promise you should never make.

**Scaling the relay.** Two naive relay instances grab the same batch and double-publish everything. The standard fix is `FOR UPDATE SKIP LOCKED` in the poll query — each relay locks the rows it's working; competitors skip them. The template ships the single-instance version deliberately: add SKIP LOCKED when you measure the need, not before.

**Polling vs. CDC.** Polling every few seconds is fine for a huge range of workloads and needs zero extra infrastructure. Debezium tailing the WAL is lower-latency and much more machinery. Start with polling.

**Cleanup.** Published rows pile up; a nightly `DELETE ... WHERE published_at < now() - interval '30 days'` keeps the table sane. The partial index doesn't care either way.

## When to skip all of this

Often, honestly. If the "event handler" lives in the same process — creating a product should warm a cache — call the method; in-process, in-transaction, done. And if losing the occasional event is tolerable (analytics pings), fire-and-forget with a retry is genuinely fine.

The outbox earns its keep exactly when a state change in *your* database must reliably reach *another* system. The moment someone says "when X happens here, Y must happen over there" — reach for it. It's maybe 200 lines including the migration, and it turns a distributed-systems problem into a table and a for loop.

!!! tip "Packaged version"
    If you'd rather not own this code, Spring's answer is the [Spring Modulith Event Publication Registry](https://docs.spring.io/spring-modulith/reference/events.html): it records events published with Spring's `ApplicationEventPublisher` in the same transaction and redelivers the incomplete ones. Same idea, different packaging.

## Try it

1. Run the stack (`make docker-up`), create a product, and watch the relay log `publishing domain event` with `event_name=product.created`. Then check the row: `SELECT event_name, published_at FROM outbox_events;`
2. Kill the app between insert and relay tick (raise `fixedRate` in `OutboxRelay` for a long poll interval), restart, and confirm the event still goes out. That's the whole pattern in one experiment.
3. Add a `ProductPriceChanged` event: record it in `updatePrice`, and check `update` in the repository — does it insert outbox events today? (Look. This is a real extension point.)

Next: [idempotent commands](08-idempotency.md) — the other half of surviving retries, this time on the way *in*.
