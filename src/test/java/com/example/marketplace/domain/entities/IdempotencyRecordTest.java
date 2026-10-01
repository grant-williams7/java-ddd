package com.example.marketplace.domain.entities;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class IdempotencyRecordTest {

    @Test
    void newIdempotencyRecord() {
        String key = "test-key-123";
        String request = "{\"product\": \"test\", \"price\": 99.99}";

        IdempotencyRecord record = IdempotencyRecord.create(key, request);

        assertThat(record.getId()).isNotNull().isNotEqualTo(Uuids.NIL);
        assertThat(record.getKey()).isEqualTo(key);
        assertThat(record.getRequest()).isEqualTo(request);
        assertThat(record.getResponse()).isEmpty();
        assertThat(record.getStatusCode()).isZero();
        assertThat(record.getCreatedAt()).isNotNull();
    }

    @Test
    void setResponse() {
        IdempotencyRecord record = IdempotencyRecord.create("test-key", "{\"test\": \"request\"}");
        assertThat(record.getResponse()).isEmpty();
        assertThat(record.getStatusCode()).isZero();

        String response = "{\"id\": \"123\", \"status\": \"created\"}";
        record.setResponse(response, 201);

        assertThat(record.getResponse()).isEqualTo(response);
        assertThat(record.getStatusCode()).isEqualTo(201);
    }

    @Test
    void setResponse_multiple() {
        IdempotencyRecord record = IdempotencyRecord.create("test-key", "{\"test\": \"request\"}");

        record.setResponse("{\"status\": \"processing\"}", 202);
        assertThat(record.getResponse()).isEqualTo("{\"status\": \"processing\"}");
        assertThat(record.getStatusCode()).isEqualTo(202);

        record.setResponse("{\"id\": \"123\", \"status\": \"completed\"}", 200);
        assertThat(record.getResponse()).isEqualTo("{\"id\": \"123\", \"status\": \"completed\"}");
        assertThat(record.getStatusCode()).isEqualTo(200);
    }

    static Stream<Arguments> setResponse_errorScenarios() {
        return Stream.of(
                Arguments.of("client error", "{\"error\": \"Bad Request\"}", 400),
                Arguments.of("server error", "{\"error\": \"Internal Server Error\"}", 500),
                Arguments.of("empty response", "", 204),
                Arguments.of("negative status", "{\"error\": \"test\"}", -1),
                Arguments.of("large response", "\0".repeat(1000), 200));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void setResponse_errorScenarios(String caseName, String response, int statusCode) {
        IdempotencyRecord record = IdempotencyRecord.create(caseName + "-key", "{\"test\": \"request\"}");

        record.setResponse(response, statusCode);

        assertThat(record.getResponse()).isEqualTo(response);
        assertThat(record.getStatusCode()).isEqualTo(statusCode);
    }

    @Test
    void immutableFields() {
        IdempotencyRecord record = IdempotencyRecord.create("immutable-test-key", "{\"test\": \"immutable\"}");
        UUID originalId = record.getId();
        String originalKey = record.getKey();
        String originalRequest = record.getRequest();
        Instant originalCreatedAt = record.getCreatedAt();

        record.setResponse("{\"status\": \"first\"}", 200);
        record.setResponse("{\"status\": \"second\"}", 201);

        assertThat(record.getId()).isEqualTo(originalId);
        assertThat(record.getKey()).isEqualTo(originalKey);
        assertThat(record.getRequest()).isEqualTo(originalRequest);
        assertThat(record.getCreatedAt()).isEqualTo(originalCreatedAt);
        assertThat(record.getResponse()).isEqualTo("{\"status\": \"second\"}");
        assertThat(record.getStatusCode()).isEqualTo(201);
    }

    @Test
    void isCompleted() {
        IdempotencyRecord record = IdempotencyRecord.create("test-key", "{\"test\": \"request\"}");
        // A fresh record marks an in-flight request.
        assertThat(record.isCompleted()).isFalse();

        record.setResponse("{\"id\": \"123\"}", 200);
        assertThat(record.isCompleted()).isTrue();
    }

    @Test
    void isCompleted_errorStatus() {
        IdempotencyRecord record = IdempotencyRecord.create("test-key", "{\"test\": \"request\"}");

        // Any non-zero status code counts as completed, even errors.
        record.setResponse("{\"error\": \"boom\"}", 500);
        assertThat(record.isCompleted()).isTrue();
    }

    @Test
    void uniqueIds() {
        Set<UUID> ids = new HashSet<>();

        for (int i = 0; i < 100; i++) {
            IdempotencyRecord record = IdempotencyRecord.create("key-" + i, "{\"test\": \"data\"}");
            assertThat(ids.add(record.getId())).as("duplicate id %s", record.getId()).isTrue();
        }

        assertThat(ids).hasSize(100);
    }

    @Test
    void createdAtConsistency() {
        Instant before = Timestamps.now();
        IdempotencyRecord record = IdempotencyRecord.create("time-test-key", "{\"test\": \"time\"}");
        Instant after = Timestamps.now();

        assertThat(record.getCreatedAt()).isBetween(before, after);
    }
}
