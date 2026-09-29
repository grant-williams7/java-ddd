package com.example.marketplace.application.services;

/** Another caller is still processing a request with the same idempotency key. */
public final class RequestInFlightException extends RuntimeException {

    public RequestInFlightException() {
        super("a request with this idempotency key is already in progress");
    }
}
