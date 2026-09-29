package com.example.marketplace.application.common;

import com.example.marketplace.domain.entities.Money;
import java.time.Instant;
import java.util.UUID;

public record ProductResult(
        UUID id,
        String name,
        Money price,
        UUID sellerId,
        Instant createdAt,
        Instant updatedAt) {
}
