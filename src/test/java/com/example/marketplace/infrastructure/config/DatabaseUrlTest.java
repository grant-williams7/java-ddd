package com.example.marketplace.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DatabaseUrlTest {

    private static final Map<String, String> NO_ENVIRONMENT = Map.of();

    @Test
    void defaultKeywordDsn() {
        DatabaseUrl url = DatabaseUrl.parse(DatabaseConfiguration.DEFAULT_DATABASE_URL, NO_ENVIRONMENT, "os-user");

        assertThat(url.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/marketplace");
        assertThat(url.user()).isEqualTo("marketplace");
        assertThat(url.password()).isEqualTo("marketplace");
        assertThat(url.properties()).containsExactly(Map.entry("sslmode", "disable"));
    }

    @Test
    void composeUrl() {
        DatabaseUrl url = DatabaseUrl.parse(
                "postgres://marketplace:marketplace@postgres:5432/marketplace?sslmode=disable", NO_ENVIRONMENT, "x");

        assertThat(url.jdbcUrl()).isEqualTo("jdbc:postgresql://postgres:5432/marketplace");
        assertThat(url.user()).isEqualTo("marketplace");
        assertThat(url.password()).isEqualTo("marketplace");
        assertThat(url.properties()).containsExactly(Map.entry("sslmode", "disable"));
    }

    @Test
    void readmeUrl_withoutPort() {
        DatabaseUrl url = DatabaseUrl.parse("postgresql://user:password@localhost/dbname?sslmode=disable",
                NO_ENVIRONMENT, "x");

        assertThat(url.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/dbname");
        assertThat(url.user()).isEqualTo("user");
    }

    @Test
    void urlUserInfoIsPercentDecoded() {
        DatabaseUrl url = DatabaseUrl.parse("postgres://app%40corp:p%40ss+word%2F@db.internal:6543/my%20db",
                NO_ENVIRONMENT, "x");

        assertThat(url.user()).isEqualTo("app@corp");
        assertThat(url.password()).isEqualTo("p@ss+word/");
        assertThat(url.jdbcUrl()).isEqualTo("jdbc:postgresql://db.internal:6543/my%20db");
    }

    @Test
    void urlIpv6Host() {
        DatabaseUrl url = DatabaseUrl.parse("postgres://u:p@[::1]:5433/db", NO_ENVIRONMENT, "x");

        assertThat(url.jdbcUrl()).isEqualTo("jdbc:postgresql://[::1]:5433/db");
    }

    @Test
    void keywordDsn_quotingAndEscapes() {
        DatabaseUrl url = DatabaseUrl.parse(
                "host = db  user=app password='it\\'s a \\\\secret' dbname='my db' application_name=api",
                NO_ENVIRONMENT, "x");

        assertThat(url.jdbcUrl()).isEqualTo("jdbc:postgresql://db:5432/my%20db");
        assertThat(url.user()).isEqualTo("app");
        assertThat(url.password()).isEqualTo("it's a \\secret");
        assertThat(url.properties()).containsEntry("ApplicationName", "api");
    }

    @Test
    void keywordDsn_emptyQuotedValue() {
        DatabaseUrl url = DatabaseUrl.parse("host=db user=app password=''", NO_ENVIRONMENT, "x");

        assertThat(url.password()).isEmpty();
    }

    @Test
    void mapsLibpqParametersToDriverProperties() {
        DatabaseUrl url = DatabaseUrl.parse(
                "postgres://u@h/d?sslmode=require&connect_timeout=3&search_path=shop&pool_max_conns=7&pool_min_conns=1",
                NO_ENVIRONMENT, "x");

        assertThat(url.properties())
                .containsEntry("sslmode", "require")
                .containsEntry("connectTimeout", "3")
                .containsEntry("options", "-c search_path=shop")
                .doesNotContainKey("pool_min_conns");
        assertThat(url.maximumPoolSize()).isEqualTo(7);
    }

    @Test
    void environmentFillsMissingFields() {
        Map<String, String> environment = Map.of(
                "PGHOST", "env-host",
                "PGPORT", "6000",
                "PGUSER", "env-user",
                "PGPASSWORD", "env-secret",
                "PGSSLMODE", "verify-full");

        DatabaseUrl url = DatabaseUrl.parse("dbname=shop", environment, "os-user");

        assertThat(url.jdbcUrl()).isEqualTo("jdbc:postgresql://env-host:6000/shop");
        assertThat(url.user()).isEqualTo("env-user");
        assertThat(url.password()).isEqualTo("env-secret");
        assertThat(url.properties()).containsEntry("sslmode", "verify-full");
    }

    @Test
    void explicitValuesWinOverEnvironment() {
        DatabaseUrl url = DatabaseUrl.parse("postgres://app@db/shop", Map.of("PGUSER", "env-user", "PGHOST", "env"),
                "os-user");

        assertThat(url.user()).isEqualTo("app");
        assertThat(url.jdbcUrl()).isEqualTo("jdbc:postgresql://db:5432/shop");
    }

    @Test
    void libpqDefaults_userIsOsUserAndDatabaseIsUser() {
        DatabaseUrl url = DatabaseUrl.parse("", NO_ENVIRONMENT, "os-user");

        assertThat(url.user()).isEqualTo("os-user");
        assertThat(url.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/os-user");
        assertThat(url.password()).isNull();
    }

    @Test
    void invalidDsn_isRejectedWithoutEchoingTheInput() {
        assertThatThrownBy(() -> DatabaseUrl.parse("invalid-dsn", NO_ENVIRONMENT, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("invalid DATABASE_URL")
                .hasMessageNotContaining("invalid-dsn");

        assertThatThrownBy(() -> DatabaseUrl.parse("host=db password='unterminated", NO_ENVIRONMENT, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("unterminated'");
    }

    @Test
    void toStringHidesThePassword() {
        DatabaseUrl url = DatabaseUrl.parse("postgres://u:hunter2@h/d", NO_ENVIRONMENT, "x");

        assertThat(url.toString()).doesNotContain("hunter2");
    }
}
