package com.example.marketplace.interfaces.api.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CanonicalUuidTest {

    @Test
    void acceptsCanonicalLowerCase() {
        assertThat(CanonicalUuid.parse("123e4567-e89b-12d3-a456-426614174000"))
                .contains(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
    }

    @Test
    void acceptsUpperCaseAndNormalizesToLowerCase() {
        assertThat(CanonicalUuid.parse("123E4567-E89B-12D3-A456-426614174000"))
                .hasValueSatisfying(uuid -> assertThat(uuid).hasToString("123e4567-e89b-12d3-a456-426614174000"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "not-a-uuid",
            "1-1-1-1-1",
            "{123e4567-e89b-12d3-a456-426614174000}",
            "urn:uuid:123e4567-e89b-12d3-a456-426614174000",
            "123e4567e89b12d3a456426614174000",
            "123e4567-e89b-12d3-a456-42661417400g",
            "123e4567+e89b-12d3-a456-426614174000",
            " 123e4567-e89b-12d3-a456-42661417400",
    })
    void rejectsEverythingElse(String value) {
        assertThat(CanonicalUuid.parse(value)).isEmpty();
    }
}
