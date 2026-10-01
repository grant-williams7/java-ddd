package com.example.marketplace.infrastructure.config;

import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Builds the connection pool from {@code DATABASE_URL}. Defaults live here,
 * next to the code that uses them, not in deployment files. Flyway,
 * {@code JdbcClient} and the transaction manager are auto-configured on top of
 * this {@code DataSource}.
 */
@Configuration(proxyBeanMethods = false)
class DatabaseConfiguration {

    static final String DEFAULT_DATABASE_URL =
            "host=localhost user=marketplace password=marketplace dbname=marketplace port=5432 sslmode=disable";

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource dataSource(Environment environment) {
        return createDataSource(environment.getProperty("DATABASE_URL"), System.getenv(),
                System.getProperty("user.name"));
    }

    /** The pool connects on first use; Flyway's migration at startup is that first use. */
    static HikariDataSource createDataSource(String databaseUrl, Map<String, String> environment, String osUser) {
        String value = databaseUrl == null || databaseUrl.isEmpty() ? DEFAULT_DATABASE_URL : databaseUrl;
        DatabaseUrl url = DatabaseUrl.parse(value, environment, osUser);

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url.jdbcUrl());
        dataSource.setUsername(url.user());
        dataSource.setPassword(url.password());
        url.properties().forEach(dataSource::addDataSourceProperty);
        if (url.maximumPoolSize() != null) {
            dataSource.setMaximumPoolSize(url.maximumPoolSize());
        }
        return dataSource;
    }
}
