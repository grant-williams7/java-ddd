package com.example.marketplace.application.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import com.example.marketplace.domain.entities.IdempotencyRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class IdempotencyTest {

    record TestResult(String value) {
    }

    private final TestJsonCodec codec = new TestJsonCodec();

    private static IdempotencyRecord completed(String key, String request, String response) {
        IdempotencyRecord record = IdempotencyRecord.create(key, request);
        record.setResponse(response, 200);
        return record;
    }

    @Test
    void emptyKeyBypasses() {
        FakeIdempotencyRepository repo = new FakeIdempotencyRepository();
        AtomicInteger executions = new AtomicInteger();

        TestResult result = new Idempotency(repo, codec).withIdempotency("", "cmd", TestResult.class, () -> {
            executions.incrementAndGet();
            return new TestResult("fresh");
        });

        assertThat(result.value()).isEqualTo("fresh");
        assertThat(executions).hasValue(1);
        assertThat(repo.reserveCalls).isZero();
        assertThat(repo.records).isEmpty();
    }

    @Test
    void cachedCompletedResponseReturned() {
        FakeIdempotencyRepository repo = new FakeIdempotencyRepository();
        repo.records.put("key-1", completed("key-1", "\"cmd\"", "{\"value\":\"cached\"}"));
        AtomicInteger executions = new AtomicInteger();

        TestResult result = new Idempotency(repo, codec).withIdempotency("key-1", "cmd", TestResult.class, () -> {
            executions.incrementAndGet();
            return new TestResult("fresh");
        });

        assertThat(result.value()).isEqualTo("cached");
        assertThat(executions).as("a cached response must not re-execute the command").hasValue(0);
    }

    @Test
    void inFlightRecordThrowsRequestInFlight() {
        FakeIdempotencyRepository repo = new FakeIdempotencyRepository();
        repo.records.put("key-1", IdempotencyRecord.create("key-1", "\"cmd\"")); // reserved, no response yet
        AtomicInteger executions = new AtomicInteger();

        assertThatThrownBy(() -> new Idempotency(repo, codec).withIdempotency("key-1", "cmd", TestResult.class, () -> {
            executions.incrementAndGet();
            return new TestResult("fresh");
        })).isInstanceOf(RequestInFlightException.class);

        assertThat(executions).hasValue(0);
    }

    @Test
    void reservationReleasedOnExecuteError() {
        FakeIdempotencyRepository repo = new FakeIdempotencyRepository();
        RuntimeException executeError = new IllegalStateException("boom");

        assertThatThrownBy(() -> new Idempotency(repo, codec).withIdempotency("key-1", "cmd", TestResult.class, () -> {
            throw executeError;
        })).isSameAs(executeError);

        assertThat(repo.deleteCalls).isEqualTo(1);
        assertThat(repo.deletedKeys).containsExactly("key-1");
        assertThat(repo.records).as("a failed execution must release the key so the client can retry").isEmpty();
    }

    @Test
    void successStoresResponse() {
        FakeIdempotencyRepository repo = new FakeIdempotencyRepository();

        TestResult result = new Idempotency(repo, codec).withIdempotency("key-1", "cmd", TestResult.class,
                () -> new TestResult("fresh"));

        assertThat(result.value()).isEqualTo("fresh");
        IdempotencyRecord record = repo.records.get("key-1");
        assertThat(record).isNotNull();
        assertThat(record.isCompleted()).isTrue();
        assertThat(record.getStatusCode()).isEqualTo(200);
        assertThat(record.getResponse()).isEqualTo("{\"value\":\"fresh\"}");
    }

    @Test
    void reserveErrorPropagates() {
        FakeIdempotencyRepository repo = new FakeIdempotencyRepository();
        repo.reserveError = new IllegalStateException("db down");

        assertThatThrownBy(() -> new Idempotency(repo, codec).withIdempotency("key-1", "cmd", TestResult.class,
                () -> new TestResult("fresh")))
                .hasMessage("db down");
    }

    @Test
    void findErrorPropagates() {
        FakeIdempotencyRepository repo = new FakeIdempotencyRepository();
        repo.records.put("key-1", IdempotencyRecord.create("key-1", "\"cmd\"")); // key already reserved
        repo.findError = new IllegalStateException("db down");

        assertThatThrownBy(() -> new Idempotency(repo, codec).withIdempotency("key-1", "cmd", TestResult.class,
                () -> new TestResult("fresh")))
                .hasMessage("db down");
    }

    @Test
    void lostRaceServesWinnersResponse() {
        RaceLosingIdempotencyRepository repo = new RaceLosingIdempotencyRepository();
        repo.records.put("key-1", completed("key-1", "\"cmd\"", "{\"value\":\"winner\"}"));
        AtomicInteger executions = new AtomicInteger();

        TestResult result = new Idempotency(repo, codec).withIdempotency("key-1", "cmd", TestResult.class, () -> {
            executions.incrementAndGet();
            return new TestResult("loser");
        });

        assertThat(result.value()).isEqualTo("winner");
        assertThat(executions).hasValue(0);
    }

    @Test
    void lostRaceStillInFlight() {
        RaceLosingIdempotencyRepository repo = new RaceLosingIdempotencyRepository();
        repo.records.put("key-1", IdempotencyRecord.create("key-1", "\"cmd\"")); // winner not finished yet

        assertThatThrownBy(() -> new Idempotency(repo, codec).withIdempotency("key-1", "cmd", TestResult.class,
                () -> new TestResult("loser")))
                .isInstanceOf(RequestInFlightException.class);
    }

    /**
     * Java has no race detector, so the race is provoked instead: all callers
     * wait on a start gate and are released together, and the test repeats.
     */
    @RepeatedTest(20)
    void concurrentSameKeyExecutesOnce() throws Exception {
        FakeIdempotencyRepository repo = new FakeIdempotencyRepository();
        Idempotency idempotency = new Idempotency(repo, codec);
        AtomicInteger executions = new AtomicInteger();
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger inFlight = new AtomicInteger();

        int callers = 8;
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(callers)) {
            for (int i = 0; i < callers; i++) {
                futures.add(executor.submit(() -> {
                    startGate.await();
                    try {
                        idempotency.withIdempotency("shared-key", "cmd", TestResult.class, () -> {
                            executions.incrementAndGet();
                            return new TestResult("fresh");
                        });
                        successes.incrementAndGet();
                    } catch (RequestInFlightException e) {
                        inFlight.incrementAndGet();
                    }
                    return null;
                }));
            }
            startGate.countDown();
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    fail("unexpected error", e);
                }
            }
        }

        assertThat(executions).as("exactly one caller may execute").hasValue(1);
        assertThat(successes.get() + inFlight.get()).isEqualTo(callers);
        assertThat(successes.get()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void keyReuseWithDifferentPayloadRejected() {
        FakeIdempotencyRepository repo = new FakeIdempotencyRepository();
        repo.records.put("key-1", completed("key-1", "\"other-cmd\"", "{\"value\":\"cached\"}"));
        AtomicInteger executions = new AtomicInteger();

        assertThatThrownBy(() -> new Idempotency(repo, codec).withIdempotency("key-1", "cmd", TestResult.class, () -> {
            executions.incrementAndGet();
            return new TestResult("fresh");
        })).isInstanceOf(IdempotencyKeyReuseException.class);

        assertThat(executions).hasValue(0);
    }

    @Test
    void staleReservationTakenOver() {
        FakeIdempotencyRepository repo = new FakeIdempotencyRepository();
        IdempotencyRecord fresh = IdempotencyRecord.create("key-1", "\"cmd\"");
        IdempotencyRecord stale = IdempotencyRecord.reconstitute(fresh.getId(), fresh.getKey(), fresh.getRequest(),
                fresh.getResponse(), fresh.getStatusCode(),
                fresh.getCreatedAt().minus(Idempotency.RESERVATION_TTL.multipliedBy(2))); // crashed holder
        repo.records.put("key-1", stale);

        TestResult result = new Idempotency(repo, codec).withIdempotency("key-1", "cmd", TestResult.class,
                () -> new TestResult("fresh"));

        assertThat(result.value()).isEqualTo("fresh");
        IdempotencyRecord record = repo.records.get("key-1");
        assertThat(record).isNotNull();
        assertThat(record.isCompleted()).isTrue();
    }
}
