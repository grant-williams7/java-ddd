package com.example.marketplace.interfaces.api.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class IdempotencyKeysTest {

    private static MockHttpServletRequest request(String header) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/");
        if (!header.isEmpty()) {
            request.addHeader(IdempotencyKeys.HEADER, header);
        }
        return request;
    }

    @Test
    void headerTakesPrecedence() {
        assertThat(IdempotencyKeys.resolve(request("from-header"), "from-body")).isEqualTo("from-header");
    }

    @Test
    void fallsBackToBody() {
        assertThat(IdempotencyKeys.resolve(request(""), "from-body")).isEqualTo("from-body");
    }

    @Test
    void emptyWhenNeitherPresent() {
        assertThat(IdempotencyKeys.resolve(request(""), "")).isEmpty();
    }
}
