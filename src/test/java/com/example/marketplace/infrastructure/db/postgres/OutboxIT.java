package com.example.marketplace.infrastructure.db.postgres;

import static com.example.marketplace.infrastructure.db.postgres.RepositoryTestSupport.createTestSeller;
import static com.example.marketplace.infrastructure.db.postgres.RepositoryTestSupport.productRepository;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.marketplace.domain.entities.Currency;
import com.example.marketplace.domain.entities.Money;
import com.example.marketplace.domain.entities.Product;
import com.example.marketplace.domain.entities.ValidatedProduct;
import com.example.marketplace.domain.entities.ValidatedSeller;
import com.example.marketplace.testhelpers.PostgresTestContainer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class OutboxIT {

    private static final String GET_UNPUBLISHED = """
            SELECT id, aggregate_id, event_name, payload, occurred_at, published_at
            FROM outbox_events
            WHERE published_at IS NULL
            ORDER BY occurred_at
            LIMIT 10""";

    private static PostgresTestContainer database;

    @BeforeAll
    static void startDatabase() {
        database = PostgresTestContainer.start();
    }

    @AfterAll
    static void stopDatabase() {
        database.close();
    }

    @Test
    void create_writesOutboxEvent() {
        ValidatedSeller seller = createTestSeller(database, "Outbox Seller");
        ValidatedProduct product = ValidatedProduct.of(
                Product.create("Outbox Product", new Money(1299, Currency.EUR), seller));

        productRepository(database).create(product);

        // The ProductCreated event is committed together with the product.
        List<Map<String, Object>> events = database.jdbcClient().sql(GET_UNPUBLISHED).query().listOfRows();
        assertThat(events).hasSize(1);

        Map<String, Object> event = events.getFirst();
        assertThat(event.get("event_name")).isEqualTo("product.created");
        assertThat(event.get("aggregate_id")).isEqualTo(product.product().getId());
        assertThat(event.get("published_at")).isNull();

        JsonNode payload = JsonMapper.builder().build().readTree(event.get("payload").toString());
        assertThat(payload.get("name").stringValue()).isEqualTo("Outbox Product");
        assertThat(payload.get("priceMinorUnits").longValue()).isEqualTo(1299);
        assertThat(payload.get("currency").stringValue()).isEqualTo("EUR");

        // Marking it published removes it from the unpublished set, as the relay does.
        database.jdbcClient().sql("UPDATE outbox_events SET published_at = NOW() WHERE id = :id")
                .param("id", (UUID) event.get("id"))
                .update();
        assertThat(database.jdbcClient().sql(GET_UNPUBLISHED).query().listOfRows()).isEmpty();
    }
}
