package com.example.marketplace.application.common;

import java.time.Instant;
import java.util.UUID;

public record SellerResult(
        UUID id,
        String name,
        Instant createdAt,
        Instant updatedAt) {
}
