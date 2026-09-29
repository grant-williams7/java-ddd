package com.example.marketplace.interfaces.api.rest;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the idempotency key for a request. The {@code Idempotency-Key}
 * header is the preferred transport (see
 * https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/);
 * the body's {@code idempotency_key} field is a fallback for clients that send
 * it there. The header wins when both are present.
 */
final class IdempotencyKeys {

    static final String HEADER = "Idempotency-Key";

    private IdempotencyKeys() {
    }

    static String resolve(HttpServletRequest request, String bodyKey) {
        String header = request.getHeader(HEADER);
        if (header != null && !header.isEmpty()) {
            return header;
        }
        return bodyKey;
    }
}
