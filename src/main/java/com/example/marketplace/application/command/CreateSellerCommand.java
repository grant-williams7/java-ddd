package com.example.marketplace.application.command;

public record CreateSellerCommand(String idempotencyKey, String name) {

    public CreateSellerCommand withIdempotencyKey(String key) {
        return new CreateSellerCommand(key, name);
    }
}
