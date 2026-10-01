package com.example.marketplace.domain.entities;

import java.time.Instant;
import java.util.UUID;

public final class IdempotencyRecord {

    private final UUID id;
    private final String key;
    private final String request;
    private String response;
    private int statusCode;
    private final Instant createdAt;

    private IdempotencyRecord(UUID id, String key, String request, String response, int statusCode,
            Instant createdAt) {
        this.id = id;
        this.key = key;
        this.request = request;
        this.response = response;
        this.statusCode = statusCode;
        this.createdAt = createdAt;
    }

    public static IdempotencyRecord create(String key, String request) {
        return new IdempotencyRecord(Uuids.newV7(), key, request, "", 0, Timestamps.now());
    }

    public static IdempotencyRecord reconstitute(UUID id, String key, String request, String response,
            int statusCode, Instant createdAt) {
        return new IdempotencyRecord(id, key, request, response, statusCode, createdAt);
    }

    public UUID getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public String getRequest() {
        return request;
    }

    public String getResponse() {
        return response;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setResponse(String response, int statusCode) {
        this.response = response;
        this.statusCode = statusCode;
    }

    /**
     * Whether the original request finished and stored its response. A record
     * without a response marks a request that is still in flight.
     */
    public boolean isCompleted() {
        return statusCode != 0;
    }
}
