package com.example.marketplace.application.services;

/**
 * Turns commands and results into text and back, so the idempotency wrapper
 * can fingerprint requests and cache responses without the application layer
 * knowing which JSON library does it. Infrastructure provides the
 * implementation.
 */
public interface ResultCodec {

    String encode(Object value);

    <T> T decode(String encoded, Class<T> type);
}
