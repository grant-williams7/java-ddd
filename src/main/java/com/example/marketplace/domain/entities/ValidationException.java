package com.example.marketplace.domain.entities;

/**
 * A domain invariant was violated. The message is always
 * {@code validation failed: <detail>}.
 */
public final class ValidationException extends DomainException {

    public ValidationException(String detail) {
        super("validation failed: " + detail);
    }
}
