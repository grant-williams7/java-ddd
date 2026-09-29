# 2. Entities that guard themselves

An **entity** is a domain object with identity and a life cycle. Two products with identical names and prices are still two different products; what makes each one *itself* is its Id, not its attributes. Entities get created, change over time, and eventually get deleted — and at every step, certain things must hold true. Those are its **invariants**.

The anemic version — the one most codebases ship — looks like this:

```java
// The class anyone can corrupt.
public class Product {
    private UUID id;
    private String name;
    private double price;

    // ...and a public getter and setter for every field
}
```

Every field settable, a no-argument constructor, no rules. The invariants exist only in the heads of the developers and in scattered `if` statements across controllers, services, and jobs. Any code can produce a product with an empty name and a negative price, and the compiler will help it do so.

## Rule one: factories, not setters

The template's [`Product`](https://github.com/<owner>/java-ddd/blob/main/src/main/java/com/example/marketplace/domain/entities/Product.java) has private fields, a private constructor, and a static factory that establishes every invariant at birth:

```java
public static Product create(String name, Money price, ValidatedSeller seller) {
    Instant now = Timestamps.now();
    UUID sellerId = seller.seller().getId();
    Product product = new Product(Uuids.newV7(), now, now, name, price, sellerId, List.of());

    product.recordEvent(ProductCreated.of(
            product.id, name, price.minorUnits(), price.currency().code(), sellerId));

    return product;
}
```

Three things are decided here, once, for the whole system:

1. **Identity is assigned by the domain.** A UUIDv7 (time-ordered, so it indexes nicely) is generated in the factory — not by the database, not by the caller. The product has its identity before it ever touches Postgres.
2. **The price is a `Money`**, which as we'll see in [chapter 3](03-value-objects.md) cannot exist in an invalid state.
3. **The seller parameter is a `ValidatedSeller`.** Not a `Seller` — a `ValidatedSeller`. You literally cannot call this method with an unvalidated one; the type doesn't fit.

That third point is the template's signature pattern, so let's take it apart.

## The validated-entity pattern

The problem it solves: in most codebases, "has this object been validated?" is a question with no answer. A `Product` in your hand might have come from the factory, from JSON deserialization, from a half-updated cache — you don't know, so defensive code re-validates everywhere, and the checks drift apart.

The template makes validation a *type*:

```java
public final class ValidatedProduct {

    private final Product product;

    private ValidatedProduct(Product product) {
        this.product = product;
    }

    /** Validates a copy, so later changes to the argument can't leak in. */
    public static ValidatedProduct of(Product product) {
        Product copy = product.copy();
        copy.validate();
        return new ValidatedProduct(copy);
    }

    public Product product() {
        return product;
    }
}
```

The constructor is private and the class is final, so the only way to obtain a `ValidatedProduct` anywhere is `ValidatedProduct.of` — and through `validate()`:

```java
void validate() {
    if (name == null || name.isEmpty()) {
        throw new ValidationException("name must not be empty");
    }
    if (price == null || price.minorUnits() == 0) {
        throw new ValidationException("price must be greater than 0");
    }
    if (sellerId == null || sellerId.equals(Uuids.NIL)) {
        throw new ValidationException("seller id must not be empty");
    }
    if (Timestamps.orZero(createdAt).isAfter(Timestamps.orZero(updatedAt))) {
        throw new ValidationException("created_at must be before updated_at");
    }
}
```

Java's access control makes this *stronger* than the pattern's Go original, where a struct embedding and an unexported flag did the job: here there's no constructor to reach for and no field to flip. `validate()` itself is package-private, so outside the `entities` package the check can only happen by going through `of`.

Now look at what downstream code can demand. The repository interface takes the validated type:

```java
public interface ProductRepository {

    Product create(ValidatedProduct product);

    // ...
}
```

The signature *is* the guarantee: nothing reaches the database without passing validation, and the compiler enforces it. No code review needed, no "did you remember to call validate()?" comment. Forgetting is a type error.

Note also that every check throws a [`ValidationException`](https://github.com/<owner>/java-ddd/blob/main/src/main/java/com/example/marketplace/domain/entities/ValidationException.java), one branch of the domain's sealed `DomainException` hierarchy. The REST layer maps that type to a 400 without parsing message strings — the domain speaks in exception types, the edge translates them.

## Mutation goes through methods

Entities change, and changes must re-establish invariants. There are no setters; fields change through methods that end in `validate()`:

```java
public void updatePrice(Money price) {
    this.price = price;
    this.updatedAt = Timestamps.now();

    validate();
}
```

Is this bulletproof? No — there's one deliberate hole: the public [`Product.reconstitute(...)`](https://github.com/<owner>/java-ddd/blob/main/src/main/java/com/example/marketplace/domain/entities/Product.java) factory, which rebuilds a product from stored state *without* validating. Repositories need it: rows written years ago must still load after today's rules have moved on, so loading can't re-run today's validation. A determined colleague can call it with nonsense. The pattern's claim is more modest and, in practice, enough: the *convenient* path and the *reviewed* path are the safe one, and the repository boundary demands the validated type. In several years of running this pattern in production code, "someone bypassed the factory" has not been the bug. The bug was always in codebases where there was no factory to bypass.

## What about validation at the API edge?

You might object: my HTTP layer already validates requests. Keep it! Edge validation and domain validation answer different questions:

- The edge asks: *is this request well-formed?* (Is `price_minor_units` a number? Is `seller_id` a UUID?)
- The domain asks: *is this a valid product?* (Is the price positive? Does the seller exist and pass validation?)

The edge check is about protocol; it produces friendly 400s fast. The domain check is about business truth, and it runs no matter where the call came from — HTTP today, a message consumer tomorrow, a backfill script at 2am. The scattered-validation problem isn't solved by choosing one location; it's solved by giving each rule its *one correct* location.

## Try it

1. Delete the `price.minorUnits() == 0` check from `validate()` and run `make test-unit` — watch which tests fail and read what they assert. The test suite documents the invariants.
2. Add a new rule: product names must be at most 200 characters. Notice you touch exactly two files — the entity and its test.
3. Try to call `productRepository.create` with a plain `Product`. Enjoy the compile error; that error is the pattern working.

Next: [value objects, starting with Money](03-value-objects.md) — the same "invalid states are unconstructible" idea, applied to values instead of identities.
