package com.example.marketplace.domain.events;

import com.example.marketplace.domain.entities.Timestamps;
import com.example.marketplace.domain.entities.Uuids;
import java.time.Instant;
import java.util.UUID;

/**
 * The fields every domain event shares. Concrete events compose one and
 * delegate to it.
 */
public record BaseEvent(UUID eventId, UUID aggregateId, Instant occurredAt) {

    public static BaseEvent forAggregate(UUID aggregateId) {
        return new BaseEvent(Uuids.newV7(), aggregateId, Timestamps.now());
    }
}
