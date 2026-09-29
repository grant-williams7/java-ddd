package com.example.marketplace.application.command;

import com.example.marketplace.domain.entities.Currency;
import java.util.UUID;

/**
 * {@code id} is always null: the domain assigns a product's identity in
 * {@code Product.create}, not the caller.
 */
public record CreateProductCommand(
        String idempotencyKey,
        UUID id,
        String name,
        long priceMinorUnits,
        Currency currency,
        UUID sellerId) {

    public CreateProductCommand withIdempotencyKey(String key) {
        return new CreateProductCommand(key, id, name, priceMinorUnits, currency, sellerId);
    }
}
