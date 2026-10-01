# 6. CQRS, commands and queries

CQRS — Command Query Responsibility Segregation — has a fearsome reputation it doesn't deserve. The intimidating version involves separate databases, event sourcing, and eventual consistency between your own read and write models. This template implements the version I actually recommend to most teams: **separate code paths for writes and reads, one database.**

The idea in one sentence: a request either *changes* the system or *asks* it something, and pretending those are the same operation makes both worse.

## Why split them at all

Writes and reads want different things:

- A **write** ("create this product") cares about invariants, validation, idempotency, events. It flows through the full domain model, because that's where the rules live.
- A **read** ("list all products") cares about shape and speed. It has *no business rules* — running a list query through entity construction and validation adds cost and implies rules that aren't there.

When one `ProductService.save()`-style method serves both, you get the classic entanglement: read endpoints paying write-path costs, and write logic gradually leaking into whatever shape the UI wanted this sprint.

## Commands

A command is a plain record that names an intent in the ubiquitous language:

```java
public record CreateProductCommand(
        String idempotencyKey,
        UUID id,
        String name,
        long priceMinorUnits,
        Currency currency,
        UUID sellerId) {
    // ...
}
```

It carries raw-ish data (`long priceMinorUnits`, not `Money`) because the command is the *request* to do something — turning its data into validated domain objects is the application service's job:

```java
@Override
public CreateProductCommandResult createProduct(CreateProductCommand command) {
    return idempotency.withIdempotency(command.idempotencyKey(), command, CreateProductCommandResult.class, () -> {
        ValidatedSeller seller = findValidatedSeller(command.sellerId());
        Money price = new Money(command.priceMinorUnits(), command.currency());

        ValidatedProduct product = ValidatedProduct.of(Product.create(command.name(), price, seller));
        productRepository.create(product);

        return new CreateProductCommandResult(ProductResultMapper.fromValidatedEntity(product));
    });
}
```

Read it as a checklist of the previous chapters doing their jobs: the idempotency wrapper ([chapter 8](08-idempotency.md)) makes the command retry-safe; the seller is loaded and *validated* before use; raw numbers become `Money` through the constructor ([chapter 3](03-value-objects.md)); the entity is created and validated ([chapter 2](02-entities.md)); the repository demands the validated type and persists product + events atomically ([chapters 5](05-repositories.md)/[7](07-domain-events-outbox.md)). Every failure along the way is a domain exception that travels up unchanged.

The service *orchestrates*; it doesn't decide. Every business rule it appears to enforce is actually enforced by a domain type it merely invokes. That's the test for whether logic is in the right layer: could a second delivery mechanism (a CLI, a queue consumer) get different business behavior by calling things differently? Here, no — the domain won't let it.

Notice also what the service *isn't*: it has no `@Service`, no `@Transactional`, no Spring import at all. The application layer is plain Java, and [`ApplicationWiring`](https://github.com/<owner>/java-ddd/blob/main/src/main/java/com/example/marketplace/bootstrap/ApplicationWiring.java) turns it into beans in one place.

(Note from Grant after the fact, I think not using `@Service` or `@Transactional` can be good practice, and I would generally encourage it. I'm internally torn on how much to prematurely un-springify)

## Queries

The read side is deliberately thinner. A query names a question, and its result is a dumb shape:

```java
public record GetProductByIdQuery(UUID id) {
}
```

```java
public record ProductResult(
        UUID id,
        String name,
        Money price,
        UUID sellerId,
        Instant createdAt,
        Instant updatedAt) {
}
```

`ProductResult` is not the entity. It looks similar today — small domain — but the distinction is load-bearing: results are **outputs**, free to grow display-oriented fields (a seller name joined in, a computed label) without touching the domain model, and consumers of query results can't call `updatePrice` on one. The entity's methods and events stay inside the write path.

This split is also your future performance escape hatch, and you've paid for it already: because reads have their own path, a hot list endpoint can drop to a hand-tuned SQL query (`JdbcClient` makes this a few lines) or a denormalized view *without any write-path change*. Full CQRS with separate read stores is this same move, continued — you only continue it when measurements say so.

## Command results: why not just return the entity?

Commands here return results (`CreateProductCommandResult` wrapping a `ProductResult`) rather than bare entities. Two reasons. Pragmatically, HTTP clients need the created resource back (the Id, the timestamps) without a second round trip. Architecturally, returning a result type instead of the entity keeps the controller unable to touch domain behavior — the entity, with its `pullEvents` and mutation methods, never crosses into the interface layer.

## What this template deliberately doesn't do

Honesty section. Real CQRS-the-brand often includes things this template skips, on purpose:

- **No command/query bus.** Services are called directly through interfaces. A bus adds indirection that pays off with cross-cutting middleware at scale; a template should show the pattern, not the framework.
- **No separate read store.** One Postgres. The split is code-level, which is where 90% of the benefit lives.
- **No event sourcing.** Domain events here are *notifications* ([chapter 7](07-domain-events-outbox.md)), not the source of truth. Event sourcing is a different commitment with a different cost profile, and conflating it with CQRS is how teams end up with both and needing neither.

If your system grows into needing those, the seams are already in the right places. That's the entire bet of this architecture: not that you'll need the heavy machinery, but that adding it later shouldn't require moving walls.

## Try it

1. Trace `POST /api/v1/products` end to end: [`ProductController.java`](https://github.com/<owner>/java-ddd/blob/main/src/main/java/com/example/marketplace/interfaces/api/rest/ProductController.java) → command → service → domain → repository. Count the layers that would change if you swapped Spring MVC for another web layer. (It's one.)
2. Add a query: products cheaper than X. Notice it's a new SQL query and a thin service method — no entity involvement, no validation dance.
3. Find where `ProductResult` gets its values in `application/services/ProductResultMapper.java`. Ask: what would a `sellerName` field on the result require? (A join in the read query. Not a change to `Product`.)

Next: [domain events and the outbox](07-domain-events-outbox.md) — how a state change reliably tells the rest of the world.
