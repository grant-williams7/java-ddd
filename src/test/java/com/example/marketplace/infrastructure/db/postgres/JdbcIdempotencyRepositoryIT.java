package com.example.marketplace.infrastructure.db.postgres;

import static com.example.marketplace.infrastructure.db.postgres.RepositoryTestSupport.idempotencyRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.marketplace.domain.entities.IdempotencyRecord;
import com.example.marketplace.testhelpers.PostgresTestContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JdbcIdempotencyRepositoryIT {

    private static PostgresTestContainer database;
    private JdbcIdempotencyRepository repository;

    @BeforeAll
    static void startDatabase() {
        database = PostgresTestContainer.start();
    }

    @AfterAll
    static void stopDatabase() {
        database.close();
    }

    @BeforeEach
    void setUp() {
        database.truncateTables();
        repository = idempotencyRepository(database);
    }

    @Test
    void reserve() {
        IdempotencyRecord record = IdempotencyRecord.create("test-key", "{\"name\": \"test product\"}");

        assertThat(repository.reserve(record)).isTrue();

        // Persisted, but not completed yet.
        assertThat(repository.findByKey("test-key")).hasValueSatisfying(found -> {
            assertThat(found.getId()).isEqualTo(record.getId());
            assertThat(found.getKey()).isEqualTo(record.getKey());
            assertThat(found.getRequest()).isEqualTo(record.getRequest());
            assertThat(found.getResponse()).isEmpty();
            assertThat(found.getStatusCode()).isZero();
            assertThat(found.isCompleted()).isFalse();
            assertThat(found.getCreatedAt()).isNotNull();
        });
    }

    @Test
    void reserve_duplicateKey() {
        IdempotencyRecord first = IdempotencyRecord.create("duplicate-key", "{\"name\": \"test product 1\"}");
        assertThat(repository.reserve(first)).isTrue();

        // A second reserve with the same key must not claim it, and must not fail.
        IdempotencyRecord second = IdempotencyRecord.create("duplicate-key", "{\"name\": \"test product 2\"}");
        assertThat(repository.reserve(second)).isFalse();

        // The first record is untouched.
        assertThat(repository.findByKey("duplicate-key")).hasValueSatisfying(found -> {
            assertThat(found.getId()).isEqualTo(first.getId());
            assertThat(found.getRequest()).isEqualTo(first.getRequest());
        });
    }

    @Test
    void findByKey() {
        IdempotencyRecord record = IdempotencyRecord.create("find-test-key", "{\"name\": \"test product\"}");
        assertThat(repository.reserve(record)).isTrue();

        assertThat(repository.findByKey("find-test-key")).hasValueSatisfying(found -> {
            assertThat(found.getId()).isEqualTo(record.getId());
            assertThat(found.getKey()).isEqualTo(record.getKey());
            assertThat(found.getRequest()).isEqualTo(record.getRequest());
            assertThat(found.getCreatedAt()).isEqualTo(record.getCreatedAt());
        });
    }

    @Test
    void findByKey_notFound() {
        assertThat(repository.findByKey("non-existent-key")).isEmpty();
    }

    @Test
    void setResponse() {
        IdempotencyRecord record = IdempotencyRecord.create("set-response-key", "{\"name\": \"test product\"}");
        assertThat(repository.reserve(record)).isTrue();

        repository.setResponse("set-response-key", "{\"id\": \"456\", \"name\": \"updated product\"}", 200);

        assertThat(repository.findByKey("set-response-key")).hasValueSatisfying(found -> {
            assertThat(found.getId()).isEqualTo(record.getId());
            assertThat(found.getRequest()).isEqualTo(record.getRequest());
            assertThat(found.getResponse()).isEqualTo("{\"id\": \"456\", \"name\": \"updated product\"}");
            assertThat(found.getStatusCode()).isEqualTo(200);
            assertThat(found.isCompleted()).isTrue();
            assertThat(found.getCreatedAt()).isEqualTo(record.getCreatedAt());
        });
    }

    /** Updating a missing key is a no-op, not an error. */
    @Test
    void setResponse_nonExistentKey() {
        assertThatCode(() -> repository.setResponse("non-existent-key", "{\"result\": \"fail\"}", 404))
                .doesNotThrowAnyException();

        assertThat(repository.findByKey("non-existent-key")).isEmpty();
    }

    @Test
    void delete_releasesKey() {
        IdempotencyRecord record = IdempotencyRecord.create("release-key", "{\"name\": \"test product\"}");
        assertThat(repository.reserve(record)).isTrue();

        repository.delete("release-key");

        assertThat(repository.findByKey("release-key")).isEmpty();
        // After the release, the key can be reserved again.
        assertThat(repository.reserve(IdempotencyRecord.create("release-key", "{\"name\": \"retry\"}"))).isTrue();
    }

    @Test
    void delete_nonExistentKey() {
        assertThatCode(() -> repository.delete("non-existent-key")).doesNotThrowAnyException();
    }

    @Test
    void reserve_largeData() {
        String largeData = "A".repeat(5000);
        String largeRequest = "{\"data\": \"" + largeData + "\"}";
        String largeResponse = "{\"result\": \"" + largeData + "\"}";
        assertThat(repository.reserve(IdempotencyRecord.create("large-data-key", largeRequest))).isTrue();

        repository.setResponse("large-data-key", largeResponse, 200);

        assertThat(repository.findByKey("large-data-key")).hasValueSatisfying(found -> {
            assertThat(found.getRequest()).isEqualTo(largeRequest);
            assertThat(found.getResponse()).isEqualTo(largeResponse);
            assertThat(found.getStatusCode()).isEqualTo(200);
        });
    }

    @Test
    void integration_workflow() {
        String key = "workflow-test-key";
        String requestData = "{\"product\": \"test\", \"price_minor_units\": 9999, \"currency\": \"USD\"}";

        // The key doesn't exist yet.
        assertThat(repository.findByKey(key)).isEmpty();

        // Reserve it: processing has started.
        IdempotencyRecord record = IdempotencyRecord.create(key, requestData);
        assertThat(repository.reserve(record)).isTrue();

        // While in flight, a concurrent reserve loses the race.
        assertThat(repository.reserve(IdempotencyRecord.create(key, requestData))).isFalse();
        assertThat(repository.findByKey(key)).hasValueSatisfying(found -> assertThat(found.isCompleted()).isFalse());

        // Store the response: processing has completed.
        String responseData = "{\"id\": \"prod-123\", \"status\": \"created\"}";
        repository.setResponse(key, responseData, 201);

        assertThat(repository.findByKey(key)).hasValueSatisfying(found -> {
            assertThat(found.getId()).isEqualTo(record.getId());
            assertThat(found.getRequest()).isEqualTo(requestData);
            assertThat(found.getResponse()).isEqualTo(responseData);
            assertThat(found.getStatusCode()).isEqualTo(201);
            assertThat(found.isCompleted()).isTrue();
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 201, 400, 404, 500})
    void statusCodes(int statusCode) {
        String key = "status-" + statusCode + "-key";
        assertThat(repository.reserve(IdempotencyRecord.create(key, "{\"test\": \"data\"}"))).isTrue();

        repository.setResponse(key, "{\"result\": \"test\"}", statusCode);

        assertThat(repository.findByKey(key))
                .hasValueSatisfying(found -> assertThat(found.getStatusCode()).isEqualTo(statusCode));
    }
}
