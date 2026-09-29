package com.example.marketplace.contract;

import static com.example.marketplace.contract.Api.get;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

@Order(8)
class H_RequestIdContractTest {

    private static final String HEADER = "X-Request-Id";

    @Test
    void an_incoming_request_id_is_echoed() {
        assertThat(get("/healthz", HEADER, "trace-123").header(HEADER)).isEqualTo("trace-123");
    }

    @Test
    void a_request_id_is_generated_when_absent() {
        String first = get("/healthz").header(HEADER);
        String second = get("/healthz").header(HEADER);

        assertThat(first).matches("[A-Za-z0-9]{32}");
        assertThat(second).matches("[A-Za-z0-9]{32}").isNotEqualTo(first);
    }

    @Test
    void error_responses_carry_a_request_id_too() {
        Api.Response response = get("/api/v1/products/" + UUID.randomUUID());

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.header(HEADER)).matches("[A-Za-z0-9]{32}");
    }
}
