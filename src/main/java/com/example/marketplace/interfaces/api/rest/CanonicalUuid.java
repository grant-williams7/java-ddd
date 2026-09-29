package com.example.marketplace.interfaces.api.rest;

import java.beans.PropertyEditorSupport;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * Accepts only the canonical 8-4-4-4-12 form the OpenAPI spec's
 * {@code format: uuid} means. {@link UUID#fromString} alone is too lenient: it
 * accepts strings like {@code 1-1-1-1-1}.
 *
 * <p>Registered as the binder's editor for {@code UUID}, so it also governs
 * path variables, and a bad id fails before the request body is read. It has
 * to be an editor: when a converter rejects a value, Spring falls back to its
 * built-in, lenient UUID editor.
 */
@ControllerAdvice
class CanonicalUuid {

    private static final int LENGTH = 36;

    static Optional<UUID> parse(String value) {
        if (value == null || value.length() != LENGTH) {
            return Optional.empty();
        }
        for (int i = 0; i < LENGTH; i++) {
            char c = value.charAt(i);
            boolean hyphenPosition = i == 8 || i == 13 || i == 18 || i == 23;
            if (hyphenPosition ? c != '-' : Character.digit(c, 16) < 0) {
                return Optional.empty();
            }
        }
        return Optional.of(UUID.fromString(value));
    }

    @InitBinder
    void registerEditor(WebDataBinder binder) {
        binder.registerCustomEditor(UUID.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(parse(text).orElseThrow(() -> new IllegalArgumentException("not a canonical UUID")));
            }
        });
    }
}
