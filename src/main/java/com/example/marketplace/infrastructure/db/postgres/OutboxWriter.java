package com.example.marketplace.infrastructure.db.postgres;

import com.example.marketplace.domain.events.DomainEvent;
import java.sql.Types;
import java.util.List;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Stores domain events in the outbox table. Call it inside the transaction of
 * the aggregate write, so the state change and its events commit or roll back
 * together.
 */
@Component
class OutboxWriter {

    private static final String INSERT_OUTBOX_EVENT = """
            INSERT INTO outbox_events (id, aggregate_id, event_name, payload, occurred_at)
            VALUES (:id, :aggregate_id, :event_name, :payload, :occurred_at)""";

    private final JdbcClient jdbc;
    private final OutboxPayloads payloads;

    OutboxWriter(JdbcClient jdbc, OutboxPayloads payloads) {
        this.jdbc = jdbc;
        this.payloads = payloads;
    }

    void insert(List<DomainEvent> events) {
        for (DomainEvent event : events) {
            jdbc.sql(INSERT_OUTBOX_EVENT)
                    .param("id", event.eventId())
                    .param("aggregate_id", event.aggregateId())
                    .param("event_name", event.eventName())
                    // Types.OTHER lets Postgres read the text as jsonb.
                    .param("payload", new SqlParameterValue(Types.OTHER, payloads.toJson(event)))
                    .param("occurred_at", JdbcTimestamps.toDatabase(event.occurredAt()))
                    .update();
        }
    }
}
