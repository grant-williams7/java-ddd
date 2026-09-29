package com.example.marketplace.infrastructure.db.postgres;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Converts between the domain's {@link Instant} and the driver's
 * {@link OffsetDateTime}, which pgjdbc maps to {@code timestamptz}.
 */
final class JdbcTimestamps {

    private JdbcTimestamps() {
    }

    static OffsetDateTime toDatabase(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    static Instant fromDatabase(OffsetDateTime timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
