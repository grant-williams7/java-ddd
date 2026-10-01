package com.example.marketplace.domain.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ValidatedProductTest {

    @Test
    void productValidation() {
        Money price = new Money(1000, Currency.USD);
        UUID sellerId = Uuids.newV7();

        Product validProduct = Product.reconstitute(null, null, null, "Valid Product", price, sellerId);
        assertThatCode(validProduct::validate).doesNotThrowAnyException();

        Product emptyName = Product.reconstitute(null, null, null, "", price, sellerId);
        assertThatThrownBy(emptyName::validate).isInstanceOf(ValidationException.class);

        Product noPrice = Product.reconstitute(null, null, null, "Product", null, sellerId);
        assertThatThrownBy(noPrice::validate).isInstanceOf(ValidationException.class);

        Product noSeller = Product.reconstitute(null, null, null, "Product", price, null);
        assertThatThrownBy(noSeller::validate).isInstanceOf(ValidationException.class);
    }

    @Test
    void newValidatedProduct() {
        ValidatedSeller validatedSeller = ValidatedSeller.of(Seller.create("Example Seller"));
        Money price = new Money(1000, Currency.USD);

        ValidatedProduct validatedProduct = ValidatedProduct.of(Product.create("Example Product", price, validatedSeller));
        assertThat(validatedProduct.isValid()).isTrue();

        Product invalidProduct = Product.create("", new Money(0, Currency.USD), validatedSeller);
        assertThatThrownBy(() -> ValidatedProduct.of(invalidProduct)).isInstanceOf(ValidationException.class);
    }
}
