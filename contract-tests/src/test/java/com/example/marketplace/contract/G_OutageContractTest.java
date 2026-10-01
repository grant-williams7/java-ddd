package com.example.marketplace.contract;

import static com.example.marketplace.contract.Api.createSeller;
import static com.example.marketplace.contract.Api.get;
import static com.example.marketplace.contract.Api.postJson;
import static com.example.marketplace.contract.Api.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * Stops the compose stack's Postgres, checks the API degrades as documented,
 * then brings Postgres back. Runs last, and only when
 * {@code CONTRACT_COMPOSE_PROJECT} names the compose project to control.
 */
@Order(100)
class G_OutageContractTest {

    private static final String PROJECT = Environment.get("CONTRACT_COMPOSE_PROJECT", null);
    private static final String COMPOSE_FILE = Environment.get("CONTRACT_COMPOSE_FILE", null);

    @BeforeAll
    static void requireCompose() {
        assumeTrue(PROJECT != null, "set CONTRACT_COMPOSE_PROJECT to run the database outage scenario");
    }

    private static void compose(String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of("docker", "compose", "-p", PROJECT));
        if (COMPOSE_FILE != null) {
            command.addAll(List.of("-f", COMPOSE_FILE));
        }
        command.addAll(List.of(arguments));
        int exit = new ProcessBuilder(command).inheritIO().start().waitFor();
        if (exit != 0) {
            throw new IllegalStateException(String.join(" ", command) + " exited with " + exit);
        }
    }

    @Test
    void a_database_outage_is_reported_and_recovered_from() throws Exception {
        String sellerId = createSeller(unique("Seller")).get("id").stringValue();

        compose("stop", "postgres");
        try {
            Api.Response readiness = get("/readyz");
            assertThat(readiness.status()).isEqualTo(503);
            assertThat(readiness.json().size()).isEqualTo(2);
            assertThat(readiness.json().get("status").stringValue()).isEqualTo("unavailable");
            assertThat(readiness.json().get("reason").stringValue()).isEqualTo("database unreachable");

            assertThat(get("/healthz").status()).isEqualTo(200);

            Api.Response list = get("/api/v1/products");
            assertThat(list.status()).isEqualTo(500);
            assertThat(list.error()).isEqualTo("Failed to fetch products");

            Api.Response read = get("/api/v1/sellers/" + sellerId);
            assertThat(read.status()).isEqualTo(500);
            assertThat(read.error()).isEqualTo("Failed to fetch seller");

            Api.Response create = postJson("/api/v1/sellers", "{\"name\":\"During outage\"}");
            assertThat(create.status()).isEqualTo(500);
            assertThat(create.error()).isEqualTo("Failed to create seller");
        } finally {
            compose("start", "postgres");
        }

        Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
        while (get("/readyz").status() != 200 && Instant.now().isBefore(deadline)) {
            Thread.sleep(500);
        }
        assertThat(get("/readyz").status()).isEqualTo(200);
        assertThat(get("/api/v1/sellers/" + sellerId).status()).isEqualTo(200);
    }
}
