package com.example.marketplace.infrastructure.db.postgres;

import com.example.marketplace.domain.entities.IdempotencyRecord;
import com.example.marketplace.domain.repositories.IdempotencyRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcIdempotencyRepository implements IdempotencyRepository {

    /** Atomically claims the key. Zero rows means another request already holds it. */
    private static final String RESERVE_IDEMPOTENCY_KEY = """
            INSERT INTO idempotency_records (id, key, request, response, status_code, created_at)
            VALUES (:id, :key, :request, '', 0, :created_at)
            ON CONFLICT (key) DO NOTHING""";

    private static final String GET_IDEMPOTENCY_RECORD_BY_KEY = """
            SELECT id, key, request, response, status_code, created_at
            FROM idempotency_records
            WHERE key = :key""";

    private static final String SET_IDEMPOTENCY_RESPONSE = """
            UPDATE idempotency_records
            SET response = :response, status_code = :status_code
            WHERE key = :key""";

    private static final String DELETE_IDEMPOTENCY_RECORD = """
            DELETE FROM idempotency_records WHERE key = :key""";

    private final JdbcClient jdbc;

    JdbcIdempotencyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean reserve(IdempotencyRecord record) {
        int rows = jdbc.sql(RESERVE_IDEMPOTENCY_KEY)
                .param("id", record.getId())
                .param("key", record.getKey())
                .param("request", record.getRequest())
                .param("created_at", JdbcTimestamps.toDatabase(record.getCreatedAt()))
                .update();

        return rows > 0;
    }

    @Override
    public Optional<IdempotencyRecord> findByKey(String key) {
        return jdbc.sql(GET_IDEMPOTENCY_RECORD_BY_KEY)
                .param("key", key)
                .query(JdbcIdempotencyRepository::recordFromRow)
                .optional();
    }

    @Override
    public void setResponse(String key, String response, int statusCode) {
        jdbc.sql(SET_IDEMPOTENCY_RESPONSE)
                .param("key", key)
                .param("response", response)
                .param("status_code", statusCode)
                .update();
    }

    @Override
    public void delete(String key) {
        jdbc.sql(DELETE_IDEMPOTENCY_RECORD).param("key", key).update();
    }

    private static IdempotencyRecord recordFromRow(ResultSet row, int rowNumber) throws SQLException {
        return IdempotencyRecord.reconstitute(
                row.getObject("id", UUID.class),
                row.getString("key"),
                row.getString("request"),
                row.getString("response"),
                row.getInt("status_code"),
                JdbcTimestamps.fromDatabase(row.getObject("created_at", OffsetDateTime.class)));
    }
}
