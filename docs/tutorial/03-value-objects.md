# 3. Value objects, starting with Money

Entities have identity; **value objects** have only value. Two `Money` values of 19.99 EUR are not "two moneys that happen to be equal" — they are the same value, the way two 7s are the same number. That definition sounds philosophical until you see what it buys you in code: value objects are immutable, validated at construction, compared by value, and safe to copy and pass anywhere.

Money is the canonical value object because getting it wrong is expensive in the most literal sense. It's also the bug I've seen most often in the wild: a `double price` field, and a team that swears the numbers "look fine".

## Why double money is broken

Not risky — broken. Binary floating point can't represent most decimal fractions exactly. The party trick is `0.1 + 0.2 == 0.3` being `false`, but the version that hurts is drift under accumulation:

```java
double total = 0;
for (int i = 0; i < 1000; i++) {
    total += 0.10; // a 10-cent fee, a thousand times
}
System.out.println(total);        // 99.9999999999986
System.out.println(total == 100); // false
```

A thousand 10-cent charges and exact comparison is already gone. Feed that into a `>= threshold` check or a reconciliation diff against your payment provider and you're in epsilon-comparison whack-a-mole.

And `double` has a second, quieter problem: **it's a bare number**. `19.99` of what? EUR? USD? Cents already? I've debugged a production incident where one service sent euros and another read the same field as cents. The type system waved the 100x pricing error through, because a `double` is a `double`.

## The Money value object

The fix, from [`domain/entities/Money.java`](https://github.com/<owner>/java-ddd/blob/main/src/main/java/com/example/marketplace/domain/entities/Money.java): integer minor units, currency attached, and an enum that refuses invalid values.

```java
public enum Currency {
    EUR, USD
}
```

```java
/**
 * An immutable amount in ISO 4217 minor units (cents for EUR/USD, yen for JPY,
 * fils for BHD) plus its currency. Integers, never floating point, so there
 * are no rounding errors. The canonical constructor is the only way to build
 * one, so an invalid {@code Money} can't exist.
 */
public record Money(long minorUnits, Currency currency) {

    /**
     * Supported currencies and their minor-unit exponent. Not every currency
     * has two decimals: JPY, KRW, CLP and ISK have 0; BHD, JOD, KWD, OMR and
     * TND have 3. When adding a currency, use its real exponent so
     * {@link #toString()} stays correct.
     */
    private static final Map<Currency, Integer> SUPPORTED_CURRENCIES = Map.of(
            Currency.EUR, 2,
            Currency.USD, 2);

    public Money {
        currency = currency == null ? new Currency("") : currency;
        if (minorUnits < 0) {
            throw new ValidationException("amount must not be negative");
        }
        if (!SUPPORTED_CURRENCIES.containsKey(currency)) {
            throw new ValidationException("unsupported currency " + quote(currency.code()));
        }
    }
    // ...
}
```

The design decisions, spelled out:

**The canonical constructor is the only way in — including for Jackson.** A record has exactly one constructor that every other path must call, and here it runs the negative check and the currency whitelist. There is no setter, no builder, no "construct raw, validate later" path to forget; even the JSON code in infrastructure goes through it (more below). Every `Money` in the entire system has passed both checks. This is [chapter 2's](02-entities.md) idea taken to its logical end: for a value object, we *can* make invalid states fully unrepresentable, because nothing needs to mutate it.

**"Minor units", not "cents".** The first version of this type called the field `cents`, and a sharp reader pointed out the trap: "cents" is only correct for currencies with two decimal places. ISO 4217 defines a *minor-unit exponent* per currency — JPY, KRW, CLP and ISK have 0 (there is no sub-yen), BHD, JOD, KWD, OMR and TND have 3. A field named `cents` invites whoever adds JPY to divide by 100 and turn ¥5000 into ¥50. The currency-neutral name plus the exponent stored in the whitelist keeps the type honest, and `toString()` formats from the exponent instead of a hardcoded 100.

**Integer minor units, not `BigDecimal`.** `long` minor units give exact addition and comparison for free, sort and index trivially in the database, and cover about ±92 quadrillion dollars. `BigDecimal` would work too, but it brings its own trap: equality includes the *scale*, so `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` is `false`, and a `Money` built on it would need `compareTo` everywhere `equals` quietly fails. For prices and balances, minor units are the boring answer that works. (For FX rates or interest math, reach for a decimal type — different problem.)

**Currency is part of equality.** A record's `equals()` compares every component, so amount *and* currency count: `new Money(1000, EUR)` doesn't equal `new Money(1000, USD)`. The euros-versus-cents class of bug now fails at the type level instead of on an accountant's spreadsheet. One Java warning: `==` compares references, not values — two equal `Money` instances are usually not `==`. Always use `equals`.

**A whitelist of currencies.** Two entries looks restrictive; that's the point. The *domain* decides which currencies the business supports. Adding one is a one-line change that forces a moment of thought — including looking up the right exponent — which is what you want when the alternative is silently accepting `"BTC"` or `"EURO"` from a client.

## Immutability changes how arithmetic looks

There's no `setMinorUnits`. If the template grew an `add`, it would return a *new* value:

```java
public Money add(Money other) {
    if (!currency.equals(other.currency())) {
        throw new ValidationException("cannot add " + other.currency() + " to " + currency);
    }
    return new Money(Math.addExact(minorUnits, other.minorUnits()), currency);
}
```

Note what the signature forces you to decide: what does `EUR + USD` mean? My answer is "an error — conversion is an explicit domain operation with a rate and a timestamp, never implicit". You may answer differently, but the value object made you answer *once*, in one place, instead of everywhere an addition happens.

## Surviving the edges: JSON

A value object is only as good as its boundaries. Money constantly crosses process edges — API, database, outbox messages — and every crossing is a chance to smuggle in an invalid value. The subtle one is JSON: serialization libraries like to reach past your rules. The domain stays free of Jackson annotations, so the JSON form lives in infrastructure, in [`MoneyJsonModule`](https://github.com/<owner>/java-ddd/blob/main/src/main/java/com/example/marketplace/infrastructure/json/MoneyJsonModule.java), whose deserializer routes decoding *back through* the constructor:

```java
@Override
public Money deserialize(JsonParser parser, DeserializationContext context) {
    JsonNode node = context.readTree(parser);
    JsonNode minorUnits = node.get("price_minor_units");
    JsonNode currency = node.get("currency");

    if (minorUnits == null || !minorUnits.isIntegralNumber() || !minorUnits.canConvertToLong()) {
        return context.reportInputMismatch(Money.class, "price_minor_units must be an integer");
    }
    if (currency != null && !currency.isString()) {
        return context.reportInputMismatch(Money.class, "currency must be a string");
    }

    try {
        return new Money(minorUnits.longValue(), new Currency(currency == null ? "" : currency.stringValue()));
    } catch (ValidationException e) {
        return context.reportInputMismatch(Money.class, "%s", e.getMessage());
    }
}
```

Why bother, when the value was valid at serialization time? Because JSON doesn't only come from you. It comes from an idempotency record written by last year's code, a queue message from another service, a fixture someone hand-edited. Revalidating on the way in costs two comparisons and closes the whole category.

At the REST edge, the DTO doesn't expose the domain type at all — it carries `price_minor_units` and `currency` as explicit primitive fields. The explicit name does real work: no client developer will ever wonder whether to send `19.99` or `1999`. And the API refuses to guess: a JSON float like `49.99`, or a string like `"4999"`, is rejected with a 400 instead of being quietly truncated to `49` or coerced.

## The sharp edges I'll tell you about myself

- **Formatting is only as good as the exponent table.** `toString()` derives its divisor from the whitelist's per-currency exponent, so JPY (0 decimals) and KWD (3) format correctly the day they're added — but only if whoever adds them looks up the right exponent. The table is the contract; a wrong entry is a wrong display everywhere.
- **Division still rounds.** Integer minor units make addition exact, but 100 split three ways is still 33+33+34. You need an allocation strategy (largest-remainder works), and if you allocate, *persist the allocation* — a refund must mirror the original split, not recompute it.
- **Parsing at ingestion boundaries is where money bugs actually live.** A bank API sending `"5000"` JPY means 5000 yen, not 50.00 of anything. Parse currency-aware, before the value ever becomes an integer in your system.

## Beyond Money

The pattern generalizes to anything defined by its value and burdened with rules: email addresses, percentages, date ranges, country codes, quantities-with-units. The test for "should this be a value object?" is simple: *do I keep validating this same shape in multiple places?* If yes, give it a record with a validating constructor, and delete the scattered checks.

## Try it

1. Add `GBP` to the whitelist (exponent 2) and write the test. One line plus one assertion — feel how cheap extending a value object is. Then imagine adding `JPY` instead: the exponent table already handles the formatting.
2. Write `subtract(Money other)` and decide what happens when the result would be negative. (There's no universally right answer: an account balance may go negative, a price may not. Your domain decides.)
3. Round-trip a `Money` through a `JsonMapper` with `MoneyJsonModule` registered (`MoneyJsonModuleTest` shows how), then hand-edit the JSON to `"currency": "XXX"` and read it again. Watch the constructor reject it.

Next: [aggregates and their boundaries](04-aggregates.md) — why `Product` holds a `sellerId` and not a `Seller`.
