package com.example.marketplace.domain.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

    @Test
    void newMoney() {
        Money money = new Money(1234, Currency.USD);

        assertThat(money.minorUnits()).isEqualTo(1234);
        assertThat(money.currency()).isEqualTo(Currency.USD);
    }

    @Test
    void newMoney_zeroAmountAllowed() {
        Money money = new Money(0, Currency.EUR);

        assertThat(money.minorUnits()).isZero();
        assertThat(money.currency()).isEqualTo(Currency.EUR);
    }

    @Test
    void newMoney_negativeAmount() {
        assertThatThrownBy(() -> new Money(-1, Currency.USD))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("must not be negative");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "GBP", "usd"})
    void newMoney_unsupportedCurrency(String code) {
        assertThatThrownBy(() -> new Money(100, new Currency(code)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("unsupported currency");
    }

    /**
     * Go's {@code IsZero} guards against an unset {@code Money{}}. Java has no
     * zero value: a missing price is {@code null}, and a {@code Money} without
     * a supported currency can't be built at all.
     */
    @Test
    void money_hasNoZeroValue() {
        Money zeroUsd = new Money(0, Currency.USD);
        assertThat(zeroUsd.currency()).isEqualTo(Currency.USD);

        assertThatThrownBy(() -> new Money(0, null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("validation failed: unsupported currency \"\"");
    }

    @ParameterizedTest
    @CsvSource({
            "1234, USD, 12.34 USD",
            "5, EUR, 0.05 EUR",
            "100, EUR, 1.00 EUR",
            "999999, USD, 9999.99 USD",
    })
    void string(long minorUnits, String currency, String expected) {
        assertThat(new Money(minorUnits, new Currency(currency))).hasToString(expected);
    }

    /**
     * Pins the formatting contract for currencies whose exponent isn't 2,
     * without adding them to the whitelist: the divisor comes from the
     * exponent, not a hard-coded 100.
     */
    @ParameterizedTest
    @CsvSource({
            "5000, 0, JPY, 5000 JPY",
            "0, 0, JPY, 0 JPY",
            "12345, 3, BHD, 12.345 BHD",
            "5, 3, BHD, 0.005 BHD",
    })
    void string_exponentAware(long minorUnits, int exponent, String code, String expected) {
        assertThat(Money.format(minorUnits, exponent, code)).isEqualTo(expected);
    }
}
