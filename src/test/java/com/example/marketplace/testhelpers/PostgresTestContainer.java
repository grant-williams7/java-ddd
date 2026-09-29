package com.example.marketplace.testhelpers;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A disposable Postgres 17 with the application's Flyway migrations applied.
 * Real unique constraints, real transactions, real {@code ON CONFLICT}.
 */
public final class PostgresTestContainer implements AutoCloseable {

    public static final String IMAGE = "postgres:17-alpine";

    private static final String USER = "testuser";
    private static final String PASSWORD = "testpass";
    private static final String DATABASE = "testdb";

    private final PostgreSQLContainer container;
    private final HikariDataSource dataSource;
    private final JdbcClient jdbcClient;
    private final TransactionTemplate transactionTemplate;

    private PostgresTestContainer(PostgreSQLContainer container) {
        this.container = container;
        this.dataSource = new HikariDataSource();
        this.dataSource.setJdbcUrl(container.getJdbcUrl());
        this.dataSource.setUsername(container.getUsername());
        this.dataSource.setPassword(container.getPassword());
        this.jdbcClient = JdbcClient.create(dataSource);
        this.transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    /** Starts a container and applies every migration. */
    public static PostgresTestContainer start() {
        PostgresTestContainer database = startEmpty();
        database.migrate();
        return database;
    }

    /** Starts a container without applying any migration. */
    public static PostgresTestContainer startEmpty() {
        PostgreSQLContainer container = new PostgreSQLContainer(IMAGE)
                .withDatabaseName(DATABASE)
                .withUsername(USER)
                .withPassword(PASSWORD);
        container.start();
        return new PostgresTestContainer(container);
    }

    public void migrate() {
        flyway(null).migrate();
    }

    public void migrateTo(String version) {
        flyway(version).migrate();
    }

    private Flyway flyway(String target) {
        var configuration = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    public PostgreSQLContainer container() {
        return container;
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public JdbcClient jdbcClient() {
        return jdbcClient;
    }

    public TransactionTemplate transactionTemplate() {
        return transactionTemplate;
    }

    /** The container as a {@code DATABASE_URL} in URL form. */
    public String databaseUrl() {
        return "postgres://%s:%s@%s:%d/%s?sslmode=disable".formatted(
                USER, PASSWORD, container.getHost(), container.getMappedPort(5432), DATABASE);
    }

    /** Cleans all test data, child tables first. */
    public void truncateTables() {
        for (String table : new String[] {"products", "idempotency_records", "outbox_events", "sellers"}) {
            jdbcClient.sql("TRUNCATE TABLE " + table + " CASCADE").update();
        }
    }

    public long count(String table) {
        return jdbcClient.sql("SELECT COUNT(*) FROM " + table).query(Long.class).single();
    }

    @Override
    public void close() {
        dataSource.close();
        container.stop();
    }
}
