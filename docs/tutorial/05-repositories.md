# 5. Repositories

A **repository** gives the domain the illusion of an in-memory collection of aggregates: get one by Id, get them all, add, update, remove. Behind the illusion sits a database — but the domain never sees it, because of one structural decision that this chapter is really about:

**The interface lives in the domain. The implementation lives in infrastructure.**

## The dependency arrow points inward

Here is the entire [`ProductRepository`](https://github.com/<owner>/java-ddd/blob/main/src/main/java/com/example/marketplace/domain/repositories/ProductRepository.java) as the domain knows it:

```java
package com.example.marketplace.domain.repositories;

public interface ProductRepository {

    Product create(ValidatedProduct product);

    Optional<Product> findById(UUID id);

    List<Product> findAll();

    Product update(ValidatedProduct product);

    void delete(UUID id);
}
```

Notice what's absent: no `DataSource`, no `JdbcClient`, no `ResultSet`, no query strings, no JPA `EntityManager`. And notice what's present: `ValidatedProduct` in the write signatures — the type-level guarantee from [chapter 2](02-entities.md) that nothing unvalidated reaches storage.

Naively, the domain would depend on the database package. The interface flips the arrow: **infrastructure depends on the domain**, by implementing an interface the domain owns. Postgres becomes a plug-in. This is dependency inversion doing real work, not ceremony:

- Application services are testable with an in-memory fake — no Docker, no mocks-of-mocks. Milliseconds per test.
- The storage engine is swappable. Not hypothetically: the Go original of this template migrated from GORM to sqlc, and its domain and application layers didn't change. This port kept the same domain and application design on top of a different language and storage library.
- Every capability the domain grants to persistence is enumerated in one small interface. There's no way to "just run a quick query" from a service — if a use case needs a new access pattern, the interface grows, visibly, in review.

## One repository per aggregate root

[Chapter 4](04-aggregates.md) drew the boundaries; repositories respect them. There's a `ProductRepository` and a `SellerRepository`, and there will never be a `ProductEventRepository` or a `MoneyRepository` — you load and store *aggregates*, whole, by their root. This is the discipline that kills the N+1/lazy-loading class of problems: nothing is ever half-loaded, and nothing inside a boundary is saved independently.

## The implementation: plain SQL with JdbcClient, not JPA

The concrete side, [`JdbcProductRepository`](https://github.com/<owner>/java-ddd/blob/main/src/main/java/com/example/marketplace/infrastructure/db/postgres/JdbcProductRepository.java), is built on Spring's `JdbcClient`: real SQL in text blocks, named parameters, and a row mapper. The queries are plain enough to review:

```sql
SELECT p.id, p.name, p.price_minor_units, p.currency, p.seller_id, p.created_at, p.updated_at
FROM products p
JOIN sellers s ON p.seller_id = s.id
WHERE p.id = :id AND p.deleted_at IS NULL AND s.deleted_at IS NULL
```

I prefer this over an ORM in a DDD codebase for a specific reason: **the mapping between rows and domain objects is where invariants leak**, and I want that mapping explicit and dumb. No reflection deciding what loads when, no dirty checking flushing changes you didn't ask for, no `save()` cascading through an object graph the aggregate design says shouldn't exist. The SQL does exactly what it says.

The honest trade-off: the Go original generated type-safe code from its SQL with sqlc, so a typo in a column name failed at build time. With `JdbcClient` the SQL is a string, and a typo fails when the query runs. That's why every repository query is covered by an integration test against a real Postgres ([chapter 9](09-testing.md)) — the tests, not a code generator, are what check the SQL.

(The soft-delete filter `deleted_at IS NULL` also shows why queries belong to the implementation: "deleted products don't exist" is enforced in every read path, in one reviewed place.)

## Reconstruction goes through the constructors

Reading a row back is a boundary crossing, and the same rule from [chapter 3](03-value-objects.md) applies — reconstruction routes through the validating `Money` constructor:

```java
/** Goes through the {@code Money} constructor, so an unsupported stored currency is an error. */
private static Product productFromRow(ResultSet row, int rowNumber) throws SQLException {
    Money price = new Money(row.getLong("price_minor_units"), new Currency(row.getString("currency")));

    return Product.reconstitute(
            row.getObject("id", UUID.class),
            JdbcTimestamps.fromDatabase(row.getObject("created_at", OffsetDateTime.class)),
            JdbcTimestamps.fromDatabase(row.getObject("updated_at", OffsetDateTime.class)),
            row.getString("name"),
            price,
            row.getObject("seller_id", UUID.class));
}
```

If someone hand-edits a row to `currency = 'XXX'`, the repository refuses to materialize it rather than letting corrupt money flow into the domain. The database is *inside* the trust boundary for most teams; treating it as slightly outside costs two comparisons and has saved me real incidents.

## Errors: domain exceptions, not strings, not nulls-with-vibes

Two deliberate choices in the read/write paths:

```java
return jdbc.sql(GET_PRODUCT_BY_ID)
        .param("id", id)
        .query(JdbcProductRepository::productFromRow)
        .optional();   // findById: absence is not an error
```

```java
if (rows == 0) {
    // Nothing matched: the product doesn't exist or is soft-deleted.
    throw new ProductNotFoundException();   // update: absence IS an error
}
```

They look inconsistent; they're not. *Finding* nothing is a normal outcome the caller asked about — an empty `Optional` lets the application layer decide it's a 404. *Updating* nothing means the caller acted on an aggregate that doesn't exist — that's `ProductNotFoundException`, a **domain** exception, so the REST layer can map it by type and no layer above infrastructure ever deals in `EmptyResultDataAccessException`. Infrastructure errors don't leak; domain errors do the traveling.

## Where transactions live

The repository owns the transaction, not the service. `create` opens one, writes the product *and* its domain events (the outbox — [chapter 7](07-domain-events-outbox.md)), reads the row back, and commits:

```java
return Objects.requireNonNull(transactions.execute(status -> {
    jdbc.sql(CREATE_PRODUCT)
            .param("id", product.getId())
            // ...
            .query()
            .singleRow();

    outbox.insert(product.pullEvents());

    return findById(product.getId()).orElseThrow(() ->
            new IllegalStateException("product " + product.getId() + " not readable after insert"));
}));
```

`TransactionTemplate` commits when the callback returns and rolls back when it throws; every `JdbcClient` call inside runs on the same connection. Why not let the application service compose transactions? Because [chapter 4](04-aggregates.md) already decided the unit of consistency: one aggregate, one transaction. The repository is exactly that unit, so the transaction hides behind the interface. The day a use case genuinely needs to commit two aggregates atomically, that's a design smell to revisit at the aggregate level first — not a reason to put `@Transactional` on every service method.

## Try it

1. Write an in-memory `ProductRepository` backed by a `ConcurrentHashMap<UUID, Product>`. Note the service tests in `src/test/java/com/example/marketplace/application/services/` already use hand-written fakes — read them and see how little code the interface demands.
2. Add `findBySellerId(UUID sellerId)`. Feel the shape of the change: one interface method, one SQL query, one implementation method. Nothing else moves.
3. Delete the `deleted_at IS NULL` clause from one query and run the integration tests (`make test`). The soft-delete tests catch it — every query is covered by tests against a real Postgres in a container, which is [chapter 9's](09-testing.md) topic.

Next: [CQRS, commands and queries](06-cqrs.md) — why the write path and the read path stopped sharing code.
