package com.example.marketplace.interfaces.api.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

record SellerResponse(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("updated_at") Instant updatedAt) {
}
