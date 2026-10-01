package com.example.marketplace.interfaces.api.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/** Explicit primitives ({@code price_minor_units}, not {@code price}), so the wire format is never ambiguous. */
record ProductResponse(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("price_minor_units") long priceMinorUnits,
        @JsonProperty("currency") String currency,
        @JsonProperty("seller_id") String sellerId,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt) {
}
