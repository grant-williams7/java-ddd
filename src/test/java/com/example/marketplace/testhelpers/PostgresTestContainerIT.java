package com.example.marketplace.testhelpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.sql.Connection;
import org.junit.jupiter.api.Test;

class PostgresTestContainerIT {

    @Test
    void setupTestDb() throws Exception {
        try (PostgresTestContainer database = PostgresTestContainer.start();
                Connection connection = database.dataSource().getConnection()) {
            assertThat(database.container()).isNotNull();
            assertThat(database.jdbcClient()).isNotNull();
            assertThat(connection.isValid(1)).isTrue();
        }
    }

    @Test
    void truncateTables() {
        try (PostgresTestContainer database = PostgresTestContainer.start()) {
            database.jdbcClient().sql("""
                    INSERT INTO sellers (id, name, created_at, updated_at)
                    VALUES (gen_random_uuid(), 'Test Seller', NOW(), NOW())""").update();
            database.jdbcClient().sql("""
                    INSERT INTO products (id, name, price_minor_units, currency, seller_id, created_at, updated_at)
                    VALUES (gen_random_uuid(), 'Test Product', 9999, 'USD',
                            (SELECT id FROM sellers LIMIT 1), NOW(), NOW())""").update();
            database.jdbcClient().sql("""
                    INSERT INTO idempotency_records (id, key, request, response, status_code, created_at)
                    VALUES (gen_random_uuid(), 'test-key', '{"test": "data"}', '{"result": "success"}', 200, NOW())""")
                    .update();

            assertThat(database.count("sellers")).isPositive();
            assertThat(database.count("products")).isPositive();
            assertThat(database.count("idempotency_records")).isPositive();

            database.truncateTables();

            assertThat(database.count("sellers")).isZero();
            assertThat(database.count("products")).isZero();
            assertThat(database.count("idempotency_records")).isZero();
        }
    }

    @Test
    void close() throws Exception {
        PostgresTestContainer database = PostgresTestContainer.start();
        try (Connection connection = database.dataSource().getConnection()) {
            assertThat(connection.isValid(1)).isTrue();
        }

        assertThatCode(database::close).doesNotThrowAnyException();
        assertThat(database.container().isRunning()).isFalse();
    }

    @Test
    void multipleTestContainers() throws Exception {
        try (PostgresTestContainer first = PostgresTestContainer.start();
                PostgresTestContainer second = PostgresTestContainer.start();
                Connection firstConnection = first.dataSource().getConnection();
                Connection secondConnection = second.dataSource().getConnection()) {
            assertThat(firstConnection.isValid(1)).isTrue();
            assertThat(secondConnection.isValid(1)).isTrue();

            assertThat(first.container()).isNotSameAs(second.container());
            assertThat(first.dataSource()).isNotSameAs(second.dataSource());
        }
    }
}
