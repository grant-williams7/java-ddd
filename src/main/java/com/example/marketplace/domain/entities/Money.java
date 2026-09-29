package com.example.marketplace.domain.entities;

import java.util.Locale;
import java.util.Map;

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

    @Override
    public String toString() {
        return format(minorUnits, SUPPORTED_CURRENCIES.get(currency), currency.code());
    }

    static String format(long minorUnits, int exponent, String code) {
        if (exponent == 0) {
            return minorUnits + " " + code;
        }

        long divisor = 1;
        for (int i = 0; i < exponent; i++) {
            divisor *= 10;
        }

        String fraction = String.format(Locale.ROOT, "%0" + exponent + "d", minorUnits % divisor);
        return (minorUnits / divisor) + "." + fraction + " " + code;
    }

    private static String quote(String value) {
        StringBuilder quoted = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> {
                    if (Character.isISOControl(c)) {
                        quoted.append(String.format(Locale.ROOT, "\\x%02x", (int) c));
                    } else {
                        quoted.append(c);
                    }
                }
            }
        }
        return quoted.append('"').toString();
    }
}
