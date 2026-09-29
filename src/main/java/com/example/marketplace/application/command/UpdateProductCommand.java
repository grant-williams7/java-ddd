package com.example.marketplace.application.command;

import com.example.marketplace.domain.entities.Currency;
import java.util.UUID;

public record UpdateProductCommand(
        String idempotencyKey,
        UUID id,
        String name,
        long priceMinorUnits,
        Currency currency,
        UUID sellerId) {

    public UpdateProductCommand withIdempotencyKey(String key) {
        return new UpdateProductCommand(key, id, name, priceMinorUnits, currency, sellerId);
    }
}
