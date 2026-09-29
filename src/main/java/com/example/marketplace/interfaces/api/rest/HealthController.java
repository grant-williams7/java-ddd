package com.example.marketplace.interfaces.api.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness only proves the process responds. Readiness also checks the
 * database, so orchestrators stop routing traffic when Postgres is gone.
 */
@RestController
class HealthController {

    private static final int VALIDATION_TIMEOUT_SECONDS = 1;

    private final DataSource dataSource;

    HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/healthz")
    HealthStatus liveness() {
        return HealthStatus.OK;
    }

    @GetMapping("/readyz")
    ResponseEntity<HealthStatus> readiness() {
        if (databaseReachable()) {
            return ResponseEntity.ok(HealthStatus.OK);
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(HealthStatus.DATABASE_UNREACHABLE);
    }

    private boolean databaseReachable() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(VALIDATION_TIMEOUT_SECONDS);
        } catch (SQLException | RuntimeException e) {
            return false;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record HealthStatus(@JsonProperty("status") String status, @JsonProperty("reason") String reason) {

        static final HealthStatus OK = new HealthStatus("ok", null);
        static final HealthStatus DATABASE_UNREACHABLE = new HealthStatus("unavailable", "database unreachable");
    }
}
