package com.example.marketplace.infrastructure.outbox;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls the outbox table and publishes unpublished events. Run exactly one
 * instance, or add {@code FOR UPDATE SKIP LOCKED} to the poll query before
 * scaling out.
 */
@Component
class OutboxRelay {

    static final int BATCH_SIZE = 100;

    private static final String GET_UNPUBLISHED_OUTBOX_EVENTS = """
            SELECT id, aggregate_id, event_name, payload, occurred_at, published_at
            FROM outbox_events
            WHERE published_at IS NULL
            ORDER BY occurred_at
            LIMIT :limit""";

    private static final String MARK_OUTBOX_EVENT_PUBLISHED = """
            UPDATE outbox_events SET published_at = NOW() WHERE id = :id""";

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final JdbcClient jdbc;
    private final Publisher publisher;

    OutboxRelay(JdbcClient jdbc, Publisher publisher) {
        this.jdbc = jdbc;
        this.publisher = publisher;
    }

    @Scheduled(initialDelay = 5, fixedRate = 5, timeUnit = TimeUnit.SECONDS)
    void relay() {
        try {
            relayBatch();
        } catch (RuntimeException e) {
            log.error("outbox relay batch failed error={}", e.toString());
        }
    }

    void relayBatch() {
        List<OutboxEvent> events = jdbc.sql(GET_UNPUBLISHED_OUTBOX_EVENTS)
                .param("limit", BATCH_SIZE)
                .query((row, rowNumber) -> new OutboxEvent(
                        row.getObject("id", UUID.class),
                        row.getString("event_name"),
                        row.getString("payload")))
                .list();

        // A failure stops the batch; unpublished events are retried next tick.
        for (OutboxEvent event : events) {
            publisher.publish(event.eventName(), event.payload());
            jdbc.sql(MARK_OUTBOX_EVENT_PUBLISHED).param("id", event.id()).update();
        }
    }

    private record OutboxEvent(UUID id, String eventName, String payload) {
    }
}
