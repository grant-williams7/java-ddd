package com.example.marketplace.contract;

import static com.example.marketplace.contract.Api.createProduct;
import static com.example.marketplace.contract.Api.createSeller;
import static com.example.marketplace.contract.Api.delete;
import static com.example.marketplace.contract.Api.postJson;
import static com.example.marketplace.contract.Api.productBody;
import static com.example.marketplace.contract.Api.putJson;
import static com.example.marketplace.contract.Api.unique;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

@Order(5)
class E_IdempotencyContractTest {

    private static final String KEY = "Idempotency-Key";

    private static String sellerId;

    @BeforeAll
    static void createSellerForProducts() {
        sellerId = createSeller(unique("Seller")).get("id").stringValue();
    }

    private static String key() {
        return "contract-" + UUID.randomUUID();
    }

    private static String sellerBody(String bodyKey, String name) {
        return bodyKey == null
                ? "{\"name\":%s}".formatted(Api.quote(name))
                : "{\"idempotency_key\":%s,\"name\":%s}".formatted(Api.quote(bodyKey), Api.quote(name));
    }

    @Test
    void the_header_wins_over_the_body_key() {
        String headerKey = key();
        String bodyKey = key();
        String body = sellerBody(bodyKey, unique("Seller"));

        Api.Response first = postJson("/api/v1/sellers", body, KEY, headerKey);
        Api.Response replay = postJson("/api/v1/sellers", body, KEY, headerKey);
        Api.Response bodyKeyOnly = postJson("/api/v1/sellers", body);

        assertThat(first.status()).isEqualTo(201);
        assertThat(replay.json().get("id")).isEqualTo(first.json().get("id"));
        // The body key was never used, because the header won: this is a new seller.
        assertThat(bodyKeyOnly.status()).isEqualTo(201);
        assertThat(bodyKeyOnly.json().get("id")).isNotEqualTo(first.json().get("id"));
    }

    @Test
    void the_body_key_works_without_a_header() {
        String body = sellerBody(key(), unique("Seller"));

        Api.Response first = postJson("/api/v1/sellers", body);
        Api.Response replay = postJson("/api/v1/sellers", body);

        assertThat(replay.status()).isEqualTo(201);
        assertThat(replay.body()).isEqualTo(first.body());
    }

    @Test
    void replays_return_the_same_status_and_body() {
        String createKey = key();
        String body = productBody(sellerId, unique("Widget"), 4999, "EUR");
        Api.Response created = postJson("/api/v1/products", body, KEY, createKey);
        Api.Response createReplay = postJson("/api/v1/products", body, KEY, createKey);
        assertThat(created.status()).isEqualTo(201);
        assertThat(createReplay.status()).isEqualTo(201);
        assertThat(createReplay.body()).isEqualTo(created.body());

        String id = created.json().get("id").stringValue();
        String updateKey = key();
        String update = productBody(sellerId, unique("Widget v2"), 5999, "EUR");
        Api.Response updated = putJson("/api/v1/products/" + id, update, KEY, updateKey);
        Api.Response updateReplay = putJson("/api/v1/products/" + id, update, KEY, updateKey);
        assertThat(updated.status()).isEqualTo(200);
        assertThat(updateReplay.status()).isEqualTo(200);
        assertThat(updateReplay.body()).isEqualTo(updated.body());

        String deleteKey = key();
        Api.Response deleted = delete("/api/v1/products/" + id, KEY, deleteKey);
        // The product is gone, so only a cached result can explain another 204.
        Api.Response deleteReplay = delete("/api/v1/products/" + id, KEY, deleteKey);
        assertThat(deleted.status()).isEqualTo(204);
        assertThat(deleteReplay.status()).isEqualTo(204);
    }

    @Test
    void a_different_payload_under_the_same_key_is_rejected() {
        String key = key();
        postJson("/api/v1/sellers", sellerBody(null, unique("Seller")), KEY, key);

        Api.Response reuse = postJson("/api/v1/sellers", sellerBody(null, unique("Other")), KEY, key);

        assertThat(reuse.status()).isEqualTo(422);
        assertThat(reuse.error()).isEqualTo("idempotency key was already used with a different request");
    }

    @Test
    void a_key_reused_for_another_operation_is_rejected() {
        String key = key();
        String id = postJson("/api/v1/sellers", sellerBody(null, unique("Seller")), KEY, key)
                .json().get("id").stringValue();

        Api.Response reuse = putJson("/api/v1/sellers", "{\"id\":\"%s\",\"name\":\"x\"}".formatted(id), KEY, key);

        assertThat(reuse.status()).isEqualTo(422);
    }

    @Test
    void concurrent_creates_with_one_key_create_exactly_one_product() throws Exception {
        String key = key();
        String name = unique("Concurrent");
        String body = productBody(sellerId, name, 100, "EUR");
        int callers = 20;
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<Api.Response>> futures = new ArrayList<>();

        try (ExecutorService executor = Executors.newFixedThreadPool(callers)) {
            for (int i = 0; i < callers; i++) {
                futures.add(executor.submit(() -> {
                    startGate.await();
                    return postJson("/api/v1/products", body, KEY, key);
                }));
            }
            startGate.countDown();

            List<Api.Response> responses = new ArrayList<>();
            for (Future<Api.Response> future : futures) {
                responses.add(future.get());
            }

            assertThat(responses).extracting(Api.Response::status).allMatch(s -> s == 201 || s == 409);
            List<Api.Response> created = responses.stream().filter(r -> r.status() == 201).toList();
            assertThat(created).isNotEmpty();
            assertThat(created).extracting(r -> r.json().get("id")).containsOnly(created.getFirst().json().get("id"));
        }

        assertThat(Database.count("SELECT COUNT(*) FROM products WHERE name = ?", name)).isOne();
    }

    @Test
    void a_failed_command_releases_its_key() {
        String key = key();

        Api.Response failed = postJson("/api/v1/products", productBody(sellerId, "", 100, "EUR"), KEY, key);
        Api.Response retry = postJson("/api/v1/products", productBody(sellerId, unique("Widget"), 100, "EUR"), KEY, key);

        assertThat(failed.status()).isEqualTo(400);
        assertThat(retry.status()).isEqualTo(201);
    }

    /**
     * Plants a reservation for {@code key} that looks exactly like one this
     * implementation would write, by copying the fingerprint of a real request.
     */
    private static void plantReservation(String key, String name, Instant createdAt) {
        String probeKey = key();
        postJson("/api/v1/sellers", sellerBody(null, name), KEY, probeKey);
        String fingerprint = (String) Database.query(
                "SELECT request FROM idempotency_records WHERE key = ?", probeKey).getFirst().get("request");

        Database.update("""
                INSERT INTO idempotency_records (id, key, request, response, status_code, created_at)
                VALUES (?, ?, ?, '', 0, ?)""",
                UUID.randomUUID(), key, fingerprint.replace(probeKey, key), Timestamp.from(createdAt));
    }

    @Test
    void a_stale_reservation_is_taken_over() {
        String key = key();
        String name = unique("Seller");
        plantReservation(key, name, Instant.now().minusSeconds(120));

        Api.Response response = postJson("/api/v1/sellers", sellerBody(null, name), KEY, key);

        assertThat(response.status()).isEqualTo(201);
        assertThat(Database.count("SELECT COUNT(*) FROM idempotency_records WHERE key = ? AND status_code <> 0", key))
                .isOne();
    }

    @Test
    void a_fresh_reservation_is_in_flight() {
        String key = key();
        String name = unique("Seller");
        plantReservation(key, name, Instant.now());

        Api.Response response = postJson("/api/v1/sellers", sellerBody(null, name), KEY, key);

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.error()).isEqualTo("a request with this idempotency key is already in progress");
    }

    @Test
    void requests_without_a_key_are_not_deduplicated() {
        String body = productBody(sellerId, unique("Widget"), 100, "EUR");

        String first = postJson("/api/v1/products", body).json().get("id").stringValue();
        String second = postJson("/api/v1/products", body).json().get("id").stringValue();

        assertThat(first).isNotEqualTo(second);
        assertThat(createProduct(sellerId, unique("Widget"), 1, "USD").get("id")).isNotNull();
    }
}
