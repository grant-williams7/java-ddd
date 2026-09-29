package com.example.marketplace.domain.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.example.marketplace.domain.entities.Uuids;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainEventsTest {

    @Test
    void newProductCreated() {
        UUID productId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();

        ProductCreated event = ProductCreated.of(productId, "Widget", 999, "USD", sellerId);

        assertThat(event.eventName()).isEqualTo("product.created");
        assertThat(event.aggregateId()).isEqualTo(productId);
        assertThat(event.sellerId()).isEqualTo(sellerId);
        assertThat(event.priceMinorUnits()).isEqualTo(999);
        assertThat(event.currency()).isEqualTo("USD");
        assertThat(event.eventId()).isNotNull().isNotEqualTo(Uuids.NIL);
        assertThat(event.occurredAt()).isCloseTo(Instant.now(), within(1, ChronoUnit.SECONDS));
    }

    @Test
    void baseEvent_uniqueIds() {
        UUID aggregateId = UUID.randomUUID();

        BaseEvent first = BaseEvent.forAggregate(aggregateId);
        BaseEvent second = BaseEvent.forAggregate(aggregateId);

        assertThat(first.eventId()).isNotEqualTo(second.eventId());
    }
}
