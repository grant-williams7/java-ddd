package com.example.marketplace.domain.entities;

import java.time.Instant;
import java.util.UUID;

public final class Seller {

    private final UUID id;
    private final Instant createdAt;
    private Instant updatedAt;
    private String name;

    private Seller(UUID id, Instant createdAt, Instant updatedAt, String name) {
        this.id = id;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.name = name;
    }

    public static Seller create(String name) {
        Instant now = Timestamps.now();
        return new Seller(Uuids.newV7(), now, now, name);
    }

    /**
     * Rebuilds a seller from stored state without validating. Only repositories
     * should call this.
     */
    public static Seller reconstitute(UUID id, Instant createdAt, Instant updatedAt, String name) {
        return new Seller(id, createdAt, updatedAt, name);
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getName() {
        return name;
    }

    public void updateName(String name) {
        this.name = name;
        this.updatedAt = Timestamps.now();

        validate();
    }

    void validate() {
        if (name == null || name.isEmpty()) {
            throw new ValidationException("name must not be empty");
        }
        if (Timestamps.orZero(createdAt).isAfter(Timestamps.orZero(updatedAt))) {
            throw new ValidationException("created_at must be before updated_at");
        }
    }

    Seller copy() {
        return new Seller(id, createdAt, updatedAt, name);
    }
}
