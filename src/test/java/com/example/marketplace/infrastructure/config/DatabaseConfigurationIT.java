package com.example.marketplace.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.marketplace.testhelpers.PostgresTestContainer;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class DatabaseConfigurationIT {

    private static PostgresTestContainer database;

    @BeforeAll
    static void startDatabase() {
        database = PostgresTestContainer.start();
    }

    @AfterAll
    static void stopDatabase() {
        database.close();
    }

    private static HikariDataSource dataSourceFor(String databaseUrl) {
        return DatabaseConfiguration.createDataSource(databaseUrl, Map.of(), "os-user");
    }

    @Test
    void newConnection() throws Exception {
        try (HikariDataSource dataSource = dataSourceFor(database.databaseUrl());
                Connection connection = dataSource.getConnection()) {
            assertThat(connection.isValid(1)).isTrue();
        }
    }

    @Test
    void newConnection_invalidDsn() {
        assertThatThrownBy(() -> dataSourceFor("invalid-dsn")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void newConnection_unreachableHost() {
        try (HikariDataSource dataSource =
                dataSourceFor("postgres://user:pass@unreachable-host:5432/db?connect_timeout=1")) {
            assertThatThrownBy(dataSource::getConnection).isInstanceOf(SQLException.class);
        }
    }

    @Test
    void newQueries() {
        try (HikariDataSource dataSource = dataSourceFor(database.databaseUrl())) {
            JdbcClient jdbc = JdbcClient.create(dataSource);

            assertThatCode(() -> jdbc.sql("""
                    SELECT p.id, p.name, p.price_minor_units, p.currency, p.seller_id, p.created_at, p.updated_at
                    FROM products p
                    JOIN sellers s ON p.seller_id = s.id
                    WHERE p.deleted_at IS NULL AND s.deleted_at IS NULL
                    ORDER BY p.created_at DESC""").query().listOfRows()).doesNotThrowAnyException();
        }
    }

    /** Building the pool doesn't connect; the first use does (Flyway, at startup). */
    @Test
    void newQueries_poolIsLazy() {
        assertThatCode(() -> dataSourceFor("postgres://user:pass@unreachable-host:5432/db").close())
                .doesNotThrowAnyException();
    }

    /** An empty value counts as unset, so the documented default applies. */
    @Test
    void emptyDatabaseUrl_usesDefault() {
        try (HikariDataSource dataSource = dataSourceFor("")) {
            assertThat(dataSource.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/marketplace");
            assertThat(dataSource.getUsername()).isEqualTo("marketplace");
        }
    }
}
