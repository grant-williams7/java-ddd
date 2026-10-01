package com.example.marketplace.contract;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;

/**
 * Marks a scenario whose expectation intentionally differs from the original Go
 * service (see migration.md §2.2 and §9). {@link #value()} says why. Against Go,
 * exactly these scenarios fail.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Tag("changed-from-go")
@interface ChangedFromGo {

    String value();
}
