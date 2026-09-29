package com.example.marketplace.interfaces.api.rest;

import com.example.marketplace.application.command.UpdateSellerCommand;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;

/** The spec requires {@code id}; a missing, null, or malformed one makes the body unreadable. */
record UpdateSellerRequest(
        @JsonProperty("idempotency_key") String idempotencyKey,
        @JsonProperty("id") String id,
        @JsonProperty("name") String name) {

    /** Empty when {@code id} is missing or isn't a UUID. */
    Optional<UpdateSellerCommand> toUpdateSellerCommand() {
        return CanonicalUuid.parse(id).map(sellerId -> new UpdateSellerCommand(idempotencyKey, sellerId, name));
    }
}
