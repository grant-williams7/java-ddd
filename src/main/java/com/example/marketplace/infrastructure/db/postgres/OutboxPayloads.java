package com.example.marketplace.infrastructure.db.postgres;

import com.example.marketplace.domain.events.DomainEvent;
import com.example.marketplace.domain.events.ProductCreated;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * The published JSON shape of each domain event. It lives here rather than on
 * the event records so the domain stays free of serialization concerns.
 */
@Component
class OutboxPayloads {

    private final JsonMapper mapper;

    OutboxPayloads(JsonMapper mapper) {
        this.mapper = mapper;
    }

    String toJson(DomainEvent event) {
        return switch (event) {
            case ProductCreated created -> mapper.writeValueAsString(new ProductCreatedPayload(
                    created.eventId(),
                    created.aggregateId(),
                    created.occurredAt(),
                    created.name(),
                    created.priceMinorUnits(),
                    created.currency(),
                    created.sellerId()));
            default -> throw new IllegalArgumentException("no outbox payload for event " + event.eventName());
        };
    }

    private record ProductCreatedPayload(
            UUID eventId,
            UUID aggregateId,
            Instant occurredAt,
            String name,
            long priceMinorUnits,
            String currency,
            UUID sellerId) {
    }
}
