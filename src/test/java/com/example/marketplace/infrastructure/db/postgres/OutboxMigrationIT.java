package com.example.marketplace.infrastructure.db.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.marketplace.domain.entities.Uuids;
import com.example.marketplace.testhelpers.PostgresTestContainer;
import java.sql.Types;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.SqlParameterValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * V4 renamed the price column to minor units and rewrote pending
 * {@code product.created} payloads from {@code PriceCents} to
 * {@code PriceMinorUnits}. Without the rewrite, a consumer of the new schema
 * would read a silent zero price from rows written before the rename.
 */
class OutboxMigrationIT {

    private static final String LEGACY_PAYLOAD = """
            {"Id":"0198c0de-0000-7000-8000-000000000001","Aggregate":"0198c0de-0000-7000-8000-000000000002",\
            "OccurredAtT":"2026-07-01T00:00:00Z","Name":"Legacy Product","PriceCents":4999,"Currency":"EUR",\
            "SellerId":"0198c0de-0000-7000-8000-000000000003"}""";

    @Test
    void v4_rewritesPendingOutboxPayloads() {
        try (PostgresTestContainer database = PostgresTestContainer.startEmpty()) {
            database.migrateTo("3");
            database.jdbcClient().sql("""
                    INSERT INTO outbox_events (id, aggregate_id, event_name, payload, occurred_at)
                    VALUES (:id, :aggregate_id, :event_name, :payload, :occurred_at)""")
                    .param("id", Uuids.newV7())
                    .param("aggregate_id", Uuids.newV7())
                    .param("event_name", "product.created")
                    .param("payload", new SqlParameterValue(Types.OTHER, LEGACY_PAYLOAD))
                    .param("occurred_at", OffsetDateTime.now())
                    .update();

            database.migrate();

            String stored = database.jdbcClient().sql("SELECT payload FROM outbox_events")
                    .query(String.class).single();
            JsonNode payload = JsonMapper.builder().build().readTree(stored);
            assertThat(payload.has("PriceCents")).isFalse();
            assertThat(payload.has("PriceMinorUnits")).isTrue();
            assertThat(payload.get("PriceMinorUnits").longValue()).isEqualTo(4999);
            assertThat(payload.get("Name").stringValue()).isEqualTo("Legacy Product");
        }
    }
}
