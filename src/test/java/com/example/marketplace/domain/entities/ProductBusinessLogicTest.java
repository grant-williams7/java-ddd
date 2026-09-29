package com.example.marketplace.domain.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProductBusinessLogicTest {

    private static final ValidatedSeller SELLER = ValidatedSeller.of(Seller.create("Test Seller"));

    @Test
    void updateName() throws InterruptedException {
        Product product = Product.create("Original Product", new Money(9999, Currency.USD), SELLER);
        Instant originalUpdatedAt = product.getUpdatedAt();

        Thread.sleep(1);

        product.updateName("Updated Product");
        assertThat(product.getName()).isEqualTo("Updated Product");
        assertThat(product.getUpdatedAt()).isAfter(originalUpdatedAt);
    }

    @Test
    void updateName_emptyName() {
        Product product = Product.create("Original Product", new Money(9999, Currency.USD), SELLER);

        assertThatThrownBy(() -> product.updateName(""))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("name must not be empty");
        // The name is changed first and validated afterwards, so it stays changed on error.
        assertThat(product.getName()).isEmpty();
    }

    @Test
    void updatePrice() throws InterruptedException {
        Product product = Product.create("Test Product", new Money(5000, Currency.USD), SELLER);
        Instant originalUpdatedAt = product.getUpdatedAt();

        Thread.sleep(1);

        Money newPrice = new Money(7550, Currency.EUR);
        product.updatePrice(newPrice);
        assertThat(product.getPrice()).isEqualTo(newPrice);
        assertThat(product.getUpdatedAt()).isAfter(originalUpdatedAt);
    }

    @Test
    void updatePrice_zeroPrice() {
        Product product = Product.create("Test Product", new Money(5000, Currency.USD), SELLER);

        Money zeroPrice = new Money(0, Currency.USD);
        assertThatThrownBy(() -> product.updatePrice(zeroPrice))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("price must be greater than 0");
        // The price is changed first and validated afterwards, so it stays changed on error.
        assertThat(product.getPrice()).isEqualTo(zeroPrice);
    }

    @Test
    void assignSeller() throws InterruptedException {
        Product product = Product.create("Test Product", new Money(5000, Currency.USD), SELLER);
        Instant originalUpdatedAt = product.getUpdatedAt();
        ValidatedSeller newSeller = ValidatedSeller.of(Seller.create("New Seller"));

        Thread.sleep(1);

        product.assignSeller(newSeller);
        assertThat(product.getSellerId()).isEqualTo(newSeller.seller().getId());
        assertThat(product.getUpdatedAt()).isAfter(originalUpdatedAt);
    }

    @Test
    void validate_createdAtAfterUpdatedAt() {
        Instant now = Instant.now();
        Product product = Product.reconstitute(null, now, now.minus(Duration.ofHours(1)),
                "Test Product", new Money(9999, Currency.USD), SELLER.seller().getId());

        assertThatThrownBy(() -> ValidatedProduct.of(product))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("created_at must be before updated_at");
    }

    static Stream<Arguments> validate_allEdgeCases() {
        UUID sellerId = SELLER.seller().getId();
        return Stream.of(
                Arguments.of("empty name", "", 1000L, sellerId, "name must not be empty"),
                Arguments.of("zero price", "Valid Product", 0L, sellerId, "price must be greater than 0"),
                Arguments.of("missing seller id", "Valid Product", 1000L, Uuids.NIL, "seller id must not be empty"),
                Arguments.of("valid product", "Valid Product", 1000L, sellerId, null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void validate_allEdgeCases(String caseName, String productName, long priceMinorUnits, UUID sellerId,
            String expectedError) {
        Instant now = Instant.now();
        Product product = Product.reconstitute(null, now, now, productName,
                new Money(priceMinorUnits, Currency.USD), sellerId);

        if (expectedError == null) {
            assertThatCode(() -> ValidatedProduct.of(product)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> ValidatedProduct.of(product))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining(expectedError);
        }
    }
}
