package com.example.marketplace.domain.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ValidatedSellerTest {

    @Test
    void sellerValidation() {
        Seller validSeller = Seller.reconstitute(null, null, null, "Valid Seller");
        assertThatCode(validSeller::validate).doesNotThrowAnyException();

        Seller invalidSeller = Seller.reconstitute(null, null, null, "");
        assertThatThrownBy(invalidSeller::validate).isInstanceOf(ValidationException.class);
    }

    @Test
    void newValidatedSeller() {
        ValidatedSeller validatedSeller = ValidatedSeller.of(Seller.create("Example Seller"));
        assertThat(validatedSeller.isValid()).isTrue();

        Seller invalidSeller = Seller.create("");
        assertThatThrownBy(() -> ValidatedSeller.of(invalidSeller)).isInstanceOf(ValidationException.class);
    }
}
