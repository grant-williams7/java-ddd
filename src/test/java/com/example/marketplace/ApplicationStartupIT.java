package com.example.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.marketplace.testhelpers.PostgresTestContainer;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** Boots the real application: Flyway, the web server, configuration, and shutdown. */
class ApplicationStartupIT {

    private static PostgresTestContainer database;

    @BeforeAll
    static void startDatabase() {
        database = PostgresTestContainer.startEmpty();
    }

    @AfterAll
    static void stopDatabase() {
        database.close();
    }

    /** Command-line arguments outrank the environment, so the developer's shell can't interfere. */
    private static ConfigurableApplicationContext start(String... arguments) {
        List<String> args = new ArrayList<>(List.of(arguments));
        if (args.stream().noneMatch(arg -> arg.startsWith("--DATABASE_URL="))) {
            args.add("--DATABASE_URL=" + database.databaseUrl());
        }
        return new SpringApplicationBuilder(MarketplaceApplication.class).run(args.toArray(String[]::new));
    }

    private static int port(ConfigurableApplicationContext context) {
        return context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static boolean portIsFree(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Test
    void flywayMigratesAnEmptyDatabaseOnStartup() throws Exception {
        try (ConfigurableApplicationContext context = start("--PORT=0")) {
            List<String> applied = database.jdbcClient()
                    .sql("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank")
                    .query(String.class).list();
            assertThat(applied).containsExactly("1", "2", "3", "4");

            HttpResponse<String> ready = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port(context) + "/readyz")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(ready.statusCode()).isEqualTo(200);
            assertThat(ready.body()).isEqualTo("{\"status\":\"ok\"}");
        }
    }

    @Test
    void unreachableDatabase_failsStartup() {
        assertThatThrownBy(() -> start("--PORT=0",
                "--DATABASE_URL=postgres://user:pass@127.0.0.1:1/db?connect_timeout=1"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void portEnvironmentVariable_overridesThePort() throws IOException {
        int port = freePort();

        try (ConfigurableApplicationContext context = start("--PORT=" + port)) {
            assertThat(port(context)).isEqualTo(port);
        }
    }

    /** An empty value counts as unset. If something else already holds 8080, trying it is proof enough. */
    @Test
    void emptyPort_fallsBackTo8080() {
        if (portIsFree(8080)) {
            try (ConfigurableApplicationContext context = start("--PORT=")) {
                assertThat(port(context)).isEqualTo(8080);
            }
        } else {
            assertThatThrownBy(() -> start("--PORT=")).hasStackTraceContaining("8080");
        }
    }

    @Test
    void gracefulShutdown_completesWithinTenSeconds() {
        ConfigurableApplicationContext context = start("--PORT=0");

        long started = System.nanoTime();
        context.close();

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(10));
        assertThat(context.isActive()).isFalse();
    }
}
