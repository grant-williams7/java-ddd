package com.example.marketplace.domain.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SellerBusinessLogicTest {

    @Test
    void updateName() throws InterruptedException {
        Seller seller = Seller.create("Original Seller");
        Instant originalUpdatedAt = seller.getUpdatedAt();

        Thread.sleep(1);

        seller.updateName("Updated Seller");
        assertThat(seller.getName()).isEqualTo("Updated Seller");
        assertThat(seller.getUpdatedAt()).isAfter(originalUpdatedAt);
    }

    @Test
    void updateName_emptyName() {
        Seller seller = Seller.create("Original Seller");

        assertThatThrownBy(() -> seller.updateName(""))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("name must not be empty");
        // The name is changed first and validated afterwards, so it stays changed on error.
        assertThat(seller.getName()).isEmpty();
    }

    @Test
    void validate_createdAtAfterUpdatedAt() {
        Instant now = Instant.now();
        Seller seller = Seller.reconstitute(null, now, now.minus(Duration.ofHours(1)), "Test Seller");

        assertThatThrownBy(() -> ValidatedSeller.of(seller))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("created_at must be before updated_at");
    }

    static Stream<Arguments> validate_allEdgeCases() {
        return Stream.of(
                Arguments.of("empty name", "", "name must not be empty"),
                Arguments.of("valid name", "Valid Seller", null),
                // Names aren't trimmed, so whitespace-only is valid.
                Arguments.of("whitespace only name", "   ", null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void validate_allEdgeCases(String caseName, String sellerName, String expectedError) {
        Instant now = Instant.now();
        Seller seller = Seller.reconstitute(null, now, now, sellerName);

        if (expectedError == null) {
            assertThat(ValidatedSeller.of(seller)).isNotNull();
        } else {
            assertThatThrownBy(() -> ValidatedSeller.of(seller))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining(expectedError);
        }
    }

    @Test
    void updateName_longName() {
        Seller seller = Seller.create("Original Seller");
        String longName = "A".repeat(1000);

        seller.updateName(longName);

        assertThat(seller.getName()).isEqualTo(longName);
    }

    @Test
    void isValid() {
        ValidatedSeller validatedSeller = ValidatedSeller.of(Seller.create("Test Seller"));

        assertThat(validatedSeller.isValid()).isTrue();
    }
}
