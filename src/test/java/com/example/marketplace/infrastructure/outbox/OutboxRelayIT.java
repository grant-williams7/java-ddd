package com.example.marketplace.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.marketplace.domain.entities.Uuids;
import com.example.marketplace.testhelpers.PostgresTestContainer;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.SqlParameterValue;

@ExtendWith(OutputCaptureExtension.class)
class OutboxRelayIT {

    private static PostgresTestContainer database;

    @BeforeAll
    static void startDatabase() {
        database = PostgresTestContainer.start();
    }

    @AfterAll
    static void stopDatabase() {
        database.close();
    }

    @BeforeEach
    void setUp() {
        database.truncateTables();
    }

    private static void insertEvent(String name, OffsetDateTime occurredAt) {
        database.jdbcClient().sql("""
                INSERT INTO outbox_events (id, aggregate_id, event_name, payload, occurred_at)
                VALUES (:id, :aggregate_id, :event_name, :payload, :occurred_at)""")
                .param("id", Uuids.newV7())
                .param("aggregate_id", Uuids.newV7())
                .param("event_name", "product.created")
                .param("payload", new SqlParameterValue(Types.OTHER, "{\"name\":\"" + name + "\"}"))
                .param("occurred_at", occurredAt)
                .update();
    }

    private static long unpublished() {
        return database.jdbcClient().sql("SELECT COUNT(*) FROM outbox_events WHERE published_at IS NULL")
                .query(Long.class).single();
    }

    @Test
    void relayBatch_publishesInOccurrenceOrderAndMarksPublished() {
        OffsetDateTime now = OffsetDateTime.now();
        insertEvent("second", now);
        insertEvent("first", now.minusSeconds(1));
        List<String> published = new ArrayList<>();

        new OutboxRelay(database.jdbcClient(), (eventName, payload) -> published.add(payload)).relayBatch();

        assertThat(published).containsExactly("{\"name\": \"first\"}", "{\"name\": \"second\"}");
        assertThat(unpublished()).isZero();
    }

    @Test
    void relayBatch_stopsAtTheFirstPublishFailure() {
        OffsetDateTime now = OffsetDateTime.now();
        insertEvent("first", now.minusSeconds(1));
        insertEvent("second", now);
        List<String> published = new ArrayList<>();
        Publisher failingOnSecond = (eventName, payload) -> {
            if (payload.contains("second")) {
                throw new IllegalStateException("broker down");
            }
            published.add(payload);
        };

        assertThatThrownBy(() -> new OutboxRelay(database.jdbcClient(), failingOnSecond).relayBatch())
                .hasMessage("broker down");

        // The failed event stays unpublished and is retried next tick.
        assertThat(published).hasSize(1);
        assertThat(unpublished()).isOne();
    }

    @Test
    void relay_logsBatchFailures(CapturedOutput output) {
        insertEvent("only", OffsetDateTime.now());

        new OutboxRelay(database.jdbcClient(), (eventName, payload) -> {
            throw new IllegalStateException("broker down");
        }).relay();

        assertThat(output).contains("outbox relay batch failed error=java.lang.IllegalStateException: broker down");
    }

    @Test
    void loggingPublisher_logsEventNameAndPayload(CapturedOutput output) {
        new LoggingPublisher().publish("product.created", "{\"name\":\"Widget\"}");

        assertThat(output).contains("publishing domain event event_name=product.created payload={\"name\":\"Widget\"}");
    }
}
