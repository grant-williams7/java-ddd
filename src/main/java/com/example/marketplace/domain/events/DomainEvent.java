package com.example.marketplace.domain.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Implemented by every event raised in the domain layer. Events are facts:
 * named in past tense and immutable once created.
 */
public interface DomainEvent {

    UUID eventId();

    String eventName();

    Instant occurredAt();

    /** The aggregate the event belongs to. */
    UUID aggregateId();
}
