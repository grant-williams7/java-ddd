# 9. Testing a DDD codebase

I don't trust my code — I test it. But *what* to test at *which* level is where teams burn time, and it's also where this architecture quietly pays out its biggest dividend: **the layering decides the testing strategy for you.**

The rule of thumb the template follows:

| Layer | What's tested | Against | Speed |
|---|---|---|---|
| Domain | Business rules, invariants, events | Nothing — plain JUnit | microseconds |
| Application | Orchestration, idempotency, error flows | In-memory fakes | milliseconds |
| Infrastructure | SQL, mapping, transactions | **Real Postgres** (Testcontainers) | seconds |

Each layer's tests answer a different question, and none of them re-answers a lower layer's question.

## Domain tests: pure functions in disguise

The domain has no dependencies — no database, no framework, no Spring context, no clock injection ceremony. So its tests are plain JUnit, and they read like the business rules they verify ([`src/test/java/.../domain/entities/`](https://github.com/<owner>/java-ddd/tree/main/src/test/java/com/example/marketplace/domain/entities)):

```java
@Test
void newMoney_negativeAmount() {
    assertThatThrownBy(() -> new Money(-1, Currency.USD))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("must not be negative");
}
```

```java
@Test
void pullEvents_clearsEvents() {
    Product product = Product.create("Widget", new Money(999, Currency.USD),
            ValidatedSeller.of(Seller.create("Acme")));

    List<DomainEvent> first = product.pullEvents();
    List<DomainEvent> second = product.pullEvents();

    assertThat(first).hasSize(1);
    assertThat(second).isEmpty();
}
```

Two things to notice. Assertions lead with the **exception type** (`isInstanceOf(ValidationException.class)`) — that's the contract the rest of the system relies on; the message check pins wording clients see in API errors, so it's there on purpose, not as the main assertion. And the *whole invariant surface* of an entity is testable without starting anything: this is the payoff of [chapter 2's](02-entities.md) insistence that rules live in the entity. When a colleague asks "what are the rules for a product?", the entity test file is a readable, executable answer.

These tests run in microseconds, so they run constantly — on save, in pre-commit, wherever. There's no excuse for an untested business rule when testing it costs this little, which is precisely the incentive structure you want.

## Application tests: fakes, not mock frameworks

Application services orchestrate; their tests verify the orchestration — commands flow, errors propagate, idempotency holds. Because repositories are interfaces owned by the domain ([chapter 5](05-repositories.md)), the tests hand services simple in-memory fakes:

```java
class FakeProductRepository implements ProductRepository {

    final List<ValidatedProduct> products = new ArrayList<>();

    @Override
    public Product create(ValidatedProduct product) {
        products.add(product);
        return product.product();
    }
    // ...
}
```

A list and some loops — no mock framework, no `when(...).thenReturn(...)` choreography, no `verify(times(1))`. I prefer hand-written fakes in this layer for a concrete reason: expectation-style mocks test *how* the service talks to its dependencies, which welds tests to the implementation. Fakes test *what ends up true*, which survives refactors. When the Go original of this template moved its persistence from GORM to sqlc, its application tests didn't change; when it moved to Java, the fakes came across nearly line for line. That's the property to protect.

The star of this layer is the idempotency suite ([chapter 8](08-idempotency.md)): eight threads released by one start gate, hammering one key, asserting exactly one execution. Concurrency tests belong here, against fast fakes, where you can afford to repeat them as often as you like (`@RepeatedTest`) — not against a container where each attempt costs seconds.

## Infrastructure tests: a real database or it doesn't count

Here's the strong opinion: **mocking the database in repository tests is worthless.** The repository's entire job is SQL, transactions, and row mapping. A test that mocks the SQL away verifies that the code calls the mock — nothing else. Every real repository bug I've seen lived exactly in the parts a mock skips: a query that ignores `deleted_at`, a mapping that flips two columns, a transaction that doesn't actually cover the outbox insert.

So the template's repository tests run against real Postgres via [Testcontainers](https://testcontainers.com), with the application's own Flyway migrations applied ([`PostgresTestContainer`](https://github.com/<owner>/java-ddd/blob/main/src/test/java/com/example/marketplace/testhelpers/PostgresTestContainer.java)):

```java
/** Starts a container without applying any migration. */
public static PostgresTestContainer startEmpty() {
    PostgreSQLContainer container = new PostgreSQLContainer(IMAGE)
            .withDatabaseName(DATABASE)
            .withUsername(USER)
            .withPassword(PASSWORD);
    container.start();
    return new PostgresTestContainer(container);
}

public void migrate() {
    flyway(null).migrate();
}
```

Each test class gets a disposable Postgres 17 in Docker: real unique constraints (the idempotency reservation *depends* on one), real transaction semantics (the outbox atomicity claim is *tested*, not asserted), real `ON CONFLICT` behavior. When the test passes, it means what it says.

The cost is seconds of startup per class, and it's paid honestly: `make test` runs these tests locally, and any CI runner with Docker can run them the same way. What you get for those seconds is the class of confidence mocks can't sell you — for example, [`JdbcProductRepositoryIT`](https://github.com/<owner>/java-ddd/blob/main/src/test/java/com/example/marketplace/infrastructure/db/postgres/JdbcProductRepositoryIT.java) proves that a failed product insert rolls back the outbox rows too. Try proving that with a mock.

## What deliberately isn't tested

Symmetry demands the other list:

- **No tests that a fake returns what the fake was told to return.** If a test can only fail when the test's own setup changes, delete it.
- **No E2E test pyramid-tip for every feature.** The template wires everything together in `ApplicationWiring` and component scanning; one startup test boots the real application, and one black-box [contract suite](https://github.com/<owner>/java-ddd/tree/main/contract-tests) exercises the running stack over HTTP. That covers the wiring. Feature behavior is already covered below, faster.
- **No coverage worship.** The main class and the bootstrap wiring are excluded from the metric. Chasing a number through wiring code is how teams end up with impressive dashboards and untested invariants.

## The feedback loop is the feature

The reason to care about this pyramid isn't ideology, it's *iteration speed*. Rule change? Domain test, microseconds. New orchestration? Fake-backed test, milliseconds. New query? One container-backed test, seconds. Each change lands in the cheapest layer that can catch its bugs — and the architecture is what made those layers separable in the first place.

That's the quiet argument for DDD I'd make to a skeptical team: forget the vocabulary — a codebase where business rules are testable in microseconds *without Docker* is a codebase where the rules actually get tested.

## Try it

1. `make test` — watch the domain and application tests finish before the first container is even up.
2. Break a query on purpose (drop the `deleted_at IS NULL` from `GET_PRODUCT_BY_ID` in `JdbcProductRepository`) and see which layer catches it. Then break a business rule (allow zero prices) and see which layer catches *that*. Different layers, by design.
3. Write the test for exercise 3 of [chapter 7](07-domain-events-outbox.md): does `update` insert outbox events for a `ProductPriceChanged`? You now have all three tools — decide which layer this test belongs in, and why. (My answer: infrastructure — it's a claim about a transaction.)

---

That's the tutorial. For the layer rules in one page, see [Architecture](../reference/architecture.md); for the questions everyone asks next, the [FAQ](../reference/faq.md). And if this codebase is the way you like to learn, [star the repo](https://github.com/<owner>/java-ddd) — it genuinely helps more people find it.
