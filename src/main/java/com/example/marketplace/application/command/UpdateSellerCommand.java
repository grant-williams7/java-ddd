package com.example.marketplace.application.command;

import java.util.UUID;

public record UpdateSellerCommand(String idempotencyKey, UUID id, String name) {

    public UpdateSellerCommand withIdempotencyKey(String key) {
        return new UpdateSellerCommand(key, id, name);
    }
}
