package com.example.marketplace.domain.events;

import java.time.Instant;
import java.util.UUID;

public record ProductCreated(
        BaseEvent base,
        String name,
        long priceMinorUnits,
        String currency,
        UUID sellerId) implements DomainEvent {

    public static final String NAME = "product.created";

    public static ProductCreated of(UUID productId, String name, long priceMinorUnits, String currency, UUID sellerId) {
        return new ProductCreated(BaseEvent.forAggregate(productId), name, priceMinorUnits, currency, sellerId);
    }

    @Override
    public UUID eventId() {
        return base.eventId();
    }

    @Override
    public String eventName() {
        return NAME;
    }

    @Override
    public Instant occurredAt() {
        return base.occurredAt();
    }

    @Override
    public UUID aggregateId() {
        return base.aggregateId();
    }
}
