package com.example.marketplace.domain.entities;

/**
 * The ISO 4217 code of a {@link Money} value. Any code can be represented;
 * whether the business supports it is decided when a {@code Money} is built.
 */
public record Currency(String code) {

    public static final Currency EUR = new Currency("EUR");
    public static final Currency USD = new Currency("USD");

    public Currency {
        code = code == null ? "" : code;
    }

    @Override
    public String toString() {
        return code;
    }
}
