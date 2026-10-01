package com.example.marketplace.application.services;

import com.example.marketplace.domain.entities.IdempotencyRecord;
import com.example.marketplace.domain.repositories.IdempotencyRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory idempotency store that counts calls and can be told to fail. */
class FakeIdempotencyRepository implements IdempotencyRepository {

    final Map<String, IdempotencyRecord> records = new HashMap<>();
    int reserveCalls;
    int deleteCalls;
    final List<String> deletedKeys = new ArrayList<>();
    RuntimeException reserveError;
    RuntimeException findError;
    RuntimeException setResponseError;

    @Override
    public synchronized boolean reserve(IdempotencyRecord record) {
        reserveCalls++;
        if (reserveError != null) {
            throw reserveError;
        }
        if (records.containsKey(record.getKey())) {
            return false;
        }
        records.put(record.getKey(), record);
        return true;
    }

    @Override
    public synchronized Optional<IdempotencyRecord> findByKey(String key) {
        if (findError != null) {
            throw findError;
        }
        IdempotencyRecord record = records.get(key);
        if (record == null) {
            return Optional.empty();
        }
        return Optional.of(IdempotencyRecord.reconstitute(record.getId(), record.getKey(), record.getRequest(),
                record.getResponse(), record.getStatusCode(), record.getCreatedAt()));
    }

    @Override
    public synchronized void setResponse(String key, String response, int statusCode) {
        if (setResponseError != null) {
            throw setResponseError;
        }
        IdempotencyRecord record = records.get(key);
        if (record != null) {
            record.setResponse(response, statusCode);
        }
    }

    @Override
    public synchronized void delete(String key) {
        deleteCalls++;
        deletedKeys.add(key);
        records.remove(key);
    }
}
