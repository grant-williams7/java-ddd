package com.example.marketplace.application.services;

import com.example.marketplace.domain.entities.IdempotencyRecord;
import com.example.marketplace.domain.entities.Timestamps;
import com.example.marketplace.domain.repositories.IdempotencyRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps a command with idempotency handling:
 * <ol>
 *   <li>The key is reserved atomically (INSERT .. ON CONFLICT DO NOTHING), so
 *       concurrent requests with the same key can't both execute.</li>
 *   <li>A completed request with the same key and payload returns its cached
 *       response; the same key with a different payload is rejected.</li>
 *   <li>On failure the reservation is released so the client can retry;
 *       reservations orphaned by a crash expire after {@link #RESERVATION_TTL}.</li>
 * </ol>
 */
final class Idempotency {

    /**
     * How long a reservation without a stored response is honored. If the
     * process crashes between reserving the key and storing the response, the
     * next retry after the TTL takes the reservation over instead of getting a
     * 409 forever.
     */
    static final Duration RESERVATION_TTL = Duration.ofMinutes(1);

    private static final int MAX_ATTEMPTS = 3;
    private static final int COMPLETED_STATUS = 200;

    private static final Logger log = LoggerFactory.getLogger(Idempotency.class);

    private final IdempotencyRepository repository;
    private final ResultCodec codec;

    Idempotency(IdempotencyRepository repository, ResultCodec codec) {
        this.repository = repository;
        this.codec = codec;
    }

    <T> T withIdempotency(String key, Object command, Class<T> resultType, Supplier<T> action) {
        if (key == null || key.isEmpty()) {
            return action.get();
        }

        String request = encodeRequest(command);
        IdempotencyRecord record = IdempotencyRecord.create(key, request);

        boolean reserved = false;
        for (int attempt = 0; attempt < MAX_ATTEMPTS && !reserved; attempt++) {
            reserved = repository.reserve(record);
            if (reserved) {
                break;
            }

            Optional<IdempotencyRecord> found = repository.findByKey(key);
            if (found.isEmpty()) {
                // Released between reserve and findByKey; try again.
                continue;
            }
            IdempotencyRecord existing = found.get();

            if (!existing.getRequest().equals(request)) {
                throw new IdempotencyKeyReuseException();
            }

            if (existing.isCompleted()) {
                return decodeCachedResponse(existing.getResponse(), resultType);
            }

            if (Duration.between(existing.getCreatedAt(), Timestamps.now()).compareTo(RESERVATION_TTL) < 0) {
                throw new RequestInFlightException();
            }

            // Stale reservation: the previous holder crashed before completing.
            // Release it and retry the reservation.
            repository.delete(key);
        }

        if (!reserved) {
            throw new RequestInFlightException();
        }

        T result;
        try {
            result = action.get();
        } catch (RuntimeException | Error failure) {
            release(key);
            throw failure;
        }

        storeResponse(key, result);

        return result;
    }

    private String encodeRequest(Object command) {
        try {
            return codec.encode(command);
        } catch (RuntimeException e) {
            throw new IllegalStateException("marshal idempotency request", e);
        }
    }

    private <T> T decodeCachedResponse(String response, Class<T> resultType) {
        try {
            return codec.decode(response, resultType);
        } catch (RuntimeException e) {
            throw new IllegalStateException("unmarshal cached idempotency response", e);
        }
    }

    /**
     * The failure may be an interrupted request thread, and the release must
     * still reach the database: the interrupt flag is cleared for the call and
     * restored afterwards.
     */
    private void release(String key) {
        runUninterrupted(() -> {
            try {
                repository.delete(key);
            } catch (RuntimeException e) {
                log.warn("failed to release idempotency key idempotency_key={} error={}", key, e.toString());
            }
        });
    }

    /**
     * Persists the result against the reserved key. Best effort: a failure is
     * logged but must not fail the operation, which already succeeded.
     */
    private void storeResponse(String key, Object result) {
        runUninterrupted(() -> {
            String response;
            try {
                response = codec.encode(result);
            } catch (RuntimeException e) {
                log.warn("failed to marshal idempotency response idempotency_key={} error={}", key, e.toString());
                return;
            }

            try {
                repository.setResponse(key, response, COMPLETED_STATUS);
            } catch (RuntimeException e) {
                log.warn("failed to persist idempotency response idempotency_key={} error={}", key, e.toString());
            }
        });
    }

    private static void runUninterrupted(Runnable work) {
        boolean interrupted = Thread.interrupted();
        try {
            work.run();
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
