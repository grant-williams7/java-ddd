package com.example.marketplace.domain.entities;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import java.util.UUID;

/**
 * Identity is assigned by the domain, not by the database. Version 7 UUIDs are
 * time-ordered, so they index well and sort by creation time.
 */
public final class Uuids {

    public static final UUID NIL = new UUID(0L, 0L);

    private static final TimeBasedEpochGenerator V7 = Generators.timeBasedEpochGenerator();

    private Uuids() {
    }

    public static UUID newV7() {
        return V7.generate();
    }
}
