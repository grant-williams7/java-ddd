package com.example.marketplace.interfaces.api.rest;

import com.example.marketplace.application.command.CreateProductCommand;
import com.example.marketplace.domain.entities.Currency;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;

record CreateProductRequest(
        @JsonProperty("idempotency_key") String idempotencyKey,
        @JsonProperty("name") String name,
        @JsonProperty("price_minor_units") long priceMinorUnits,
        @JsonProperty("currency") String currency,
        @JsonProperty("seller_id") String sellerId) {

    /** Empty when {@code seller_id} isn't a UUID. */
    Optional<CreateProductCommand> toCreateProductCommand() {
        return CanonicalUuid.parse(sellerId).map(seller ->
                new CreateProductCommand(idempotencyKey, null, name, priceMinorUnits, new Currency(currency), seller));
    }
}
