package com.example.marketplace.contract;

import static com.example.marketplace.contract.Api.createProduct;
import static com.example.marketplace.contract.Api.createSeller;
import static com.example.marketplace.contract.Api.unique;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Order(9)
class I_OutboxContractTest {

    private static List<Map<String, Object>> events(UUID productId) {
        return Database.query("""
                SELECT event_name, payload::text AS payload, published_at
                FROM outbox_events WHERE aggregate_id = ?""", productId);
    }

    @Test
    void a_created_product_is_published_within_the_relay_interval() throws InterruptedException {
        String sellerId = createSeller(unique("Seller")).get("id").stringValue();
        UUID productId = UUID.fromString(createProduct(sellerId, unique("Widget"), 100, "EUR").get("id").stringValue());

        List<Map<String, Object>> stored = events(productId);
        assertThat(stored).hasSize(1);
        assertThat(stored.getFirst().get("event_name")).isEqualTo("product.created");

        Instant deadline = Instant.now().plus(Duration.ofSeconds(11));
        while (events(productId).getFirst().get("published_at") == null && Instant.now().isBefore(deadline)) {
            Thread.sleep(250);
        }
        assertThat(events(productId).getFirst().get("published_at")).isNotNull();
    }

    @ChangedFromGo("§2.3 item 2: payload keys are camelCase; Go wrote its field names (Id, Aggregate, OccurredAtT, ...)")
    @Test
    void the_payload_uses_camel_case_keys() {
        String sellerId = createSeller(unique("Seller")).get("id").stringValue();
        String name = unique("Widget");
        UUID productId = UUID.fromString(createProduct(sellerId, name, 1299, "USD").get("id").stringValue());

        JsonNode payload = JsonMapper.builder().build().readTree((String) events(productId).getFirst().get("payload"));

        assertThat(payload.get("aggregateId").stringValue()).isEqualTo(productId.toString());
        assertThat(payload.get("eventId").stringValue()).isNotEmpty();
        assertThat(payload.get("occurredAt").stringValue()).matches(A_HappyPathsContractTest.UTC_TIMESTAMP);
        assertThat(payload.get("name").stringValue()).isEqualTo(name);
        assertThat(payload.get("priceMinorUnits").longValue()).isEqualTo(1299);
        assertThat(payload.get("currency").stringValue()).isEqualTo("USD");
        assertThat(payload.get("sellerId").stringValue()).isEqualTo(sellerId);
    }
}
