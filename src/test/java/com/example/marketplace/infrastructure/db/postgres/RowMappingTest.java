package com.example.marketplace.infrastructure.db.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class RowMappingTest {

    @Test
    void toDatabase() {
        Instant now = Instant.now();

        OffsetDateTime timestamp = JdbcTimestamps.toDatabase(now);

        assertThat(timestamp.getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(timestamp.toInstant()).isEqualTo(now);
    }

    /** Go's zero time has no Java counterpart: an unset timestamp is null in both directions. */
    @Test
    void toDatabase_null() {
        assertThat(JdbcTimestamps.toDatabase(null)).isNull();
    }

    @Test
    void fromDatabase() {
        OffsetDateTime timestamp = OffsetDateTime.now(ZoneOffset.ofHours(2));

        assertThat(JdbcTimestamps.fromDatabase(timestamp)).isEqualTo(timestamp.toInstant());
    }

    @Test
    void fromDatabase_null() {
        assertThat(JdbcTimestamps.fromDatabase(null)).isNull();
    }

    @Test
    void roundTrip() {
        Instant original = Instant.now().truncatedTo(ChronoUnit.MICROS); // Postgres precision

        assertThat(JdbcTimestamps.fromDatabase(JdbcTimestamps.toDatabase(original))).isEqualTo(original);
    }
}
