package com.example.marketplace.domain.entities;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * The domain's clock. Postgres stores timestamps with microsecond precision,
 * so the domain works at that precision too: what a create returns is then
 * exactly what a later read returns.
 */
public final class Timestamps {

    /** Stands in for a timestamp that was never set when comparing. */
    private static final Instant ZERO = Instant.parse("0001-01-01T00:00:00Z");

    private Timestamps() {
    }

    public static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    static Instant orZero(Instant instant) {
        return instant == null ? ZERO : instant;
    }
}
