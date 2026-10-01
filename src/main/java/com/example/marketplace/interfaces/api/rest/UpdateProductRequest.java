package com.example.marketplace.interfaces.api.rest;

import com.example.marketplace.application.command.UpdateProductCommand;
import com.example.marketplace.domain.entities.Currency;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;
import java.util.UUID;

record UpdateProductRequest(
        @JsonProperty("idempotency_key") String idempotencyKey,
        @JsonProperty("name") String name,
        @JsonProperty("price_minor_units") long priceMinorUnits,
        @JsonProperty("currency") String currency,
        @JsonProperty("seller_id") String sellerId) {

    /** The product id comes from the URL path, not the body. Empty when {@code seller_id} isn't a UUID. */
    Optional<UpdateProductCommand> toUpdateProductCommand(UUID id) {
        return CanonicalUuid.parse(sellerId).map(seller ->
                new UpdateProductCommand(idempotencyKey, id, name, priceMinorUnits, new Currency(currency), seller));
    }
}
