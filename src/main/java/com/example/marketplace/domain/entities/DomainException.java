package com.example.marketplace.domain.entities;

/**
 * Base type for every error the domain raises. Outer layers translate the
 * subtypes into HTTP status codes (404, 400) by type, never by message.
 */
public abstract sealed class DomainException extends RuntimeException
        permits ValidationException, ProductNotFoundException, SellerNotFoundException {

    DomainException(String message) {
        super(message);
    }
}
