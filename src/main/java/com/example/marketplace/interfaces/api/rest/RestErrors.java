package com.example.marketplace.interfaces.api.rest;

import com.example.marketplace.application.services.IdempotencyKeyReuseException;
import com.example.marketplace.application.services.RequestInFlightException;
import com.example.marketplace.domain.entities.ProductNotFoundException;
import com.example.marketplace.domain.entities.SellerNotFoundException;
import com.example.marketplace.domain.entities.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps well-known service errors to status codes, so clients get a 404 or 409
 * instead of a generic 500. Errors are matched by type, never by message.
 */
@RestControllerAdvice
class RestErrors {

    static final String UNREADABLE_BODY = "Failed to parse request body";

    private static final Logger log = LoggerFactory.getLogger(RestErrors.class);

    static ResponseEntity<Object> commandError(RuntimeException error, String fallback) {
        return switch (error) {
            case ProductNotFoundException _, SellerNotFoundException _ ->
                    error(HttpStatus.NOT_FOUND, error.getMessage());
            case ValidationException _ -> error(HttpStatus.BAD_REQUEST, error.getMessage());
            case RequestInFlightException _ -> error(HttpStatus.CONFLICT, error.getMessage());
            case IdempotencyKeyReuseException _ -> error(HttpStatus.UNPROCESSABLE_CONTENT, error.getMessage());
            default -> serverError(error, fallback);
        };
    }

    static ResponseEntity<Object> serverError(RuntimeException error, String message) {
        log.error(message, error);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    static ResponseEntity<Object> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(message));
    }

    /** Malformed JSON, wrong types, or a missing body. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Object> unreadableBody(HttpMessageNotReadableException exception) {
        return error(HttpStatus.BAD_REQUEST, UNREADABLE_BODY);
    }
}
