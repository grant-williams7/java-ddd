package com.example.marketplace.application.command;

import java.util.UUID;

public record DeleteSellerCommand(String idempotencyKey, UUID id) {
}
