package com.example.marketplace.application.services;

import com.example.marketplace.domain.entities.IdempotencyRecord;
import java.util.Optional;

/**
 * Simulates losing the reservation race: the first lookup sees no record, but
 * every reserve fails because a concurrent request claimed the key.
 */
class RaceLosingIdempotencyRepository extends FakeIdempotencyRepository {

    private boolean firstFind;

    @Override
    public synchronized Optional<IdempotencyRecord> findByKey(String key) {
        if (!firstFind) {
            firstFind = true;
            return Optional.empty();
        }
        return super.findByKey(key);
    }

    @Override
    public synchronized boolean reserve(IdempotencyRecord record) {
        return false;
    }
}
