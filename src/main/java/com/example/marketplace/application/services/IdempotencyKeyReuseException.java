package com.example.marketplace.application.services;

/** An idempotency key was reused with a different payload, or for a different operation. */
public final class IdempotencyKeyReuseException extends RuntimeException {

    public IdempotencyKeyReuseException() {
        super("idempotency key was already used with a different request");
    }
}
