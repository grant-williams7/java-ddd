package com.example.marketplace.domain.repositories;

import com.example.marketplace.domain.entities.IdempotencyRecord;
import java.util.Optional;

public interface IdempotencyRepository {

    /**
     * Atomically claims the record's key. Returns false when another request
     * already holds it.
     */
    boolean reserve(IdempotencyRecord record);

    Optional<IdempotencyRecord> findByKey(String key);

    void setResponse(String key, String response, int statusCode);

    /**
     * Releases a reserved key, for example when the operation failed and the
     * client should be able to retry.
     */
    void delete(String key);
}
