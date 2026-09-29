# FAQ

The questions I get most, answered the way I'd answer them over coffee.

## Isn't DDD overkill for my project?

Sometimes, yes. If your service is a thin CRUD layer — forms in, rows out, no rules beyond "required field" — then entities, value objects, and repository indirection are ceremony without payoff. Use a plain controller-and-database structure and be happy.

The switch flips when *rules* arrive: money, inventory, state machines, permissions, anything where a business person answers "well, it depends" when you ask how it should behave. At that point the question isn't whether the rules get encoded — they will be, somewhere — but whether they live in one guarded place or are smeared across controllers and jobs. My rule of thumb: **if you've written the same `if` in two places, or you can't answer "where is the rule about X?" in ten seconds, you've crossed the threshold.**

And it's not all-or-nothing. A `Money` value object pays for itself in any codebase that touches prices, even one that adopts nothing else from this template.

## DDD vs Clean Architecture vs Hexagonal — which one is this?

They're the same picture at the resolution that matters: dependencies point inward, the domain doesn't know about infrastructure, and the boundary between them is interfaces the inner layer owns. Hexagonal calls them ports and adapters, Clean Architecture calls them use cases and gateways, DDD adds the *modelling* vocabulary — entities, value objects, aggregates — that the other two are largely silent about.

This template is onion-layered in structure and DDD in modelling. If you arrived here fluent in hexagonal: `domain/repositories` are ports, `infrastructure/db/postgres` are adapters, and the DDD content is everything the ports carry.

## Why is there so much code for a two-entity marketplace?

Because the template optimizes for *copying the shape*, not for minimal line count. The marketplace is deliberately trivial so the patterns stay visible; the value is that every pattern is carried to production standard — validation you can't bypass, idempotency that survives races, an outbox that's actually transactional, tests against a real database.

A tutorial that shows 20% of a pattern teaches you to ship 20% of it. The gaps between demo-grade and production-grade (the TTL takeover in idempotency, the partial index on the outbox, interrupt-safe cleanup in failure paths) are exactly the parts you can't google your way to when the incident hits.

## Why `JdbcClient` instead of JPA/Hibernate or Spring Data?

The Go original of this template started with an ORM (GORM) and moved to hand-written SQL on purpose: a deliberate downgrade in magic. The same reasoning holds in Java. In a DDD codebase the row-to-aggregate mapping is a *boundary* where invariants can leak, so I want it explicit, dumb, and reviewable. `JdbcClient` runs the SQL you wrote and maps rows with a method you wrote — no dirty checking, no lazy loading, no cascades, no surprise queries, no entity classes shaped by what the ORM needs rather than what the domain needs. ORMs solve "I don't want to write SQL"; DDD's repositories need the opposite: SQL that does exactly what the aggregate design says and nothing more.

The trade-off is that the SQL isn't checked at build time, so every query is covered by an integration test against a real Postgres. Nothing in the architecture *requires* `JdbcClient`, though. The repository interfaces don't know it exists — swapping it for JPA means touching `infrastructure/` only. That's the point of the layering.

## Why is there a public `reconstitute` factory? Doesn't that undo the protection?

Entity fields are private and the constructors are private too, so `create` is the front door — it assigns identity, stamps timestamps, and records events. But repositories have a legitimate need the front door can't serve: loading a row that was written years ago, under rules that may have changed since. Loading can't re-run today's validation, or old data would become unreadable. `reconstitute(...)` is that deliberate side door: it rebuilds an entity from stored state without validating or recording events.

Could a colleague call it with nonsense and skip validation? Yes, and review should catch it — the same way review catches someone swallowing an exception. What keeps the model non-anemic isn't that the door is locked — it's that **behavior lives on the entity** (`updatePrice`, `assignSeller`, event recording), every mutation path re-validates, and the `ValidatedProduct` wrapper makes "went through validation" a compile-time fact at the repository boundary.

## Where are the domain services / factories / specifications / …?

Not every DDD pattern earns its place in a small domain. The template includes a pattern when the marketplace genuinely exercises it, and omits it when it would be decorative. A domain service (logic spanning multiple aggregates that belongs to no single one) would appear the day a rule like "a seller's total listed value may not exceed X" shows up — and it would be a plain class in the domain package with no Spring annotation, not a framework.

The same restraint applies to CQRS: no bus, no separate read store, no event sourcing. See [chapter 6](../tutorial/06-cqrs.md) for what's deliberately skipped and why the seams for adding it later are already in place.

## How do I add my own aggregate?

The mechanical checklist, in the order that keeps the compiler helping you:

1. **Domain**: entity + `create` + `reconstitute` + `validate()` + `ValidatedX` wrapper in `domain/entities/`; events if other systems care; repository interface in `domain/repositories/`.
2. **Schema**: a new `V<N>__description.sql` migration in `src/main/resources/db/migration/`.
3. **Infrastructure**: `JdbcXRepository` implementing the interface, with the SQL in text blocks and rows mapped through the constructors.
4. **Application**: command and query records + service, wrapping writes in `withIdempotency`; a factory method in `ApplicationServices`.
5. **Interface**: DTOs, controller, error mapping entries; the service bean in `ApplicationWiring`.
6. **Tests at each layer as you go** — domain tests first; they're the cheapest and they pin the rules before any plumbing exists.

Chapter-by-chapter, that's the whole [tutorial](../tutorial/01-the-domain.md) in reverse.

## Does this scale to microservices / multiple bounded contexts?

The template is one bounded context in one deployable, and that's the honest starting point for almost everyone. The DDD concept that governs the split — the **bounded context** — is about language: when "product" starts meaning different things to different parts of the business, you have two contexts, whether they deploy together or not.

What the template already gives you for that future: aggregates that reference by Id (no shared object graphs to untangle), domain events with an outbox (the integration mechanism between contexts), and a domain layer with no infrastructure entanglement (the part you'd lift out). Extracting a context from here is moving packages, not rewriting models.

## Why `Id` and not `ID`?

Java's camelCase settles it: `sellerId`, `getId()`, `aggregateId`. The Go original had to choose between `SellerId` and `SellerID` and enforced its choice with linter settings; here the language convention already agrees with it.

## Something's wrong / missing / could be better

Open an [issue](https://github.com/<owner>/java-ddd/issues) or a [discussion](https://github.com/<owner>/java-ddd/discussions) — pattern questions are as welcome as bug reports; there's an issue template specifically for them. And if the template or this tutorial taught you something, [a star](https://github.com/<owner>/java-ddd) helps the next person find it.
