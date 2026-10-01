package com.example.marketplace.application.command;

import java.util.UUID;

public record DeleteProductCommand(String idempotencyKey, UUID id) {
}
