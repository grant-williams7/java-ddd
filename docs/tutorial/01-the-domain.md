# 1. The domain and its language

Before a single line of Java, DDD asks one thing of you: agree on what the words mean.

That sounds trivial. It never is. I've sat in meetings where "order" meant three different things to three different teams — the cart before checkout, the paid transaction, and the fulfillment job — and the codebase faithfully reflected the confusion with `Order`, `OrderV2`, and `PurchaseOrder` classes that half-overlapped.

DDD calls the fix the **ubiquitous language**: one vocabulary, shared by developers and domain experts, used *everywhere* — in conversation, in tickets, and crucially in code. If the business says "seller", the class is `Seller`, the table is `sellers`, the endpoint is `/sellers`. No `Vendor` in the API and `Merchant` in the database because two developers had different tastes.

## The marketplace we're modelling

The template models the smallest domain that still exercises real DDD patterns: a marketplace.

- A **Seller** is someone who lists things for sale. A seller has a name.
- A **Product** is something a seller offers. A product has a name, a price, and belongs to exactly one seller.
- A **Price** is money: an amount *and* a currency. "19.99" is not a price; "19.99 EUR" is.

Deliberately boring. The point of the template is the patterns, not the domain — but even this tiny domain forces the interesting decisions: Can a product exist without a seller? (No.) Can a price be zero? (For a product, no.) What happens to products when a seller is deleted? Every one of those questions is a *business* question, and the code should answer it in exactly one place.

## Where the language lives in the code

Look at the package layout under [`src/main/java/com/example/marketplace/`](https://github.com/<owner>/java-ddd/tree/main/src/main/java/com/example/marketplace):

```
com/example/marketplace/
├── domain/           # the model: entities, value objects, events, repository interfaces
│   ├── entities/     # Product, Seller, Money, ...
│   ├── events/       # ProductCreated, ...
│   └── repositories/ # ProductRepository, SellerRepository (interfaces only)
├── application/      # use cases: commands, queries, services
├── infrastructure/   # Postgres, JdbcClient, the outbox relay — the details
├── interfaces/       # REST controllers, DTOs — the delivery mechanism
└── bootstrap/        # the one place where the layers are wired together
```

The `domain` package is the heart, and it's the layer with the fewest imports. Open [`domain/entities/Product.java`](https://github.com/<owner>/java-ddd/blob/main/src/main/java/com/example/marketplace/domain/entities/Product.java) and check its import block: `java.time`, `java.util`, and the domain's own `events` package. No Spring, no JDBC, no Jackson annotations. The domain doesn't know HTTP or SQL exist — and that isn't left to discipline: [`ModuleStructureTest`](https://github.com/<owner>/java-ddd/blob/main/src/test/java/com/example/marketplace/ModuleStructureTest.java) fails the build the day it stops being true.

That's not an aesthetic preference. It's what makes the language *stable*. HTTP frameworks and database libraries churn; "a product belongs to a seller" doesn't. When the model is free of infrastructure, the code that encodes business knowledge survives every migration. The Go original of this template swapped its whole persistence layer (GORM for sqlc) without touching a domain rule, and this port changed languages the same way: every rule, and every error message, came over one-to-one.

## Naming is design

A few naming decisions in the template worth noticing, because each encodes a rule:

- `Product.create(String name, Money price, ValidatedSeller seller)` — the factory takes a `ValidatedSeller`, not a `Seller`. The type signature says: *you cannot attach a product to a seller that hasn't passed validation.* More on this in [chapter 2](02-entities.md).
- `Money`, not `double` — a price without a currency is a bug waiting for an exchange rate. [Chapter 3](03-value-objects.md) is all about this.
- `UUID sellerId` on `Product`, not `Seller seller` — products reference sellers by identity; they don't own them. That's an aggregate boundary, and it's [chapter 4](04-aggregates.md).

The pattern behind all three: **make the language do work**. Every time a business rule can be expressed as a type instead of a comment, the compiler becomes the reviewer who never gets tired.

## Try it

Clone the repo and find the answers in the code — each should take under a minute, which is itself the point:

```bash
git clone https://github.com/grant-williams7/java-ddd.git && cd java-ddd
```

1. What are *all* the rules for a valid product? (One method answers this: `validate()` in `domain/entities/Product.java`.)
2. Can any code construct a `Money` with a negative amount? (Try it: write a test that calls `new Money(-1, Currency.USD)` and see what the compiler and the constructor let you do.)
3. Where would a new rule like "product names must be unique per seller" go? Think about it now; [chapter 4](04-aggregates.md) gives my answer.

Next: [entities that guard themselves](02-entities.md).
