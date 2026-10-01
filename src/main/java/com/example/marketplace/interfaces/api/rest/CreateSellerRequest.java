package com.example.marketplace.interfaces.api.rest;

import com.example.marketplace.application.command.CreateSellerCommand;
import com.fasterxml.jackson.annotation.JsonProperty;

record CreateSellerRequest(
        @JsonProperty("idempotency_key") String idempotencyKey,
        @JsonProperty("name") String name) {

    CreateSellerCommand toCreateSellerCommand() {
        return new CreateSellerCommand(idempotencyKey, name);
    }
}
