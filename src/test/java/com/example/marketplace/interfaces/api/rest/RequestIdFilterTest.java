package com.example.marketplace.interfaces.api.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void echoesTheCallersRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/healthz");
        request.addHeader(RequestIdFilter.HEADER, "trace-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("trace-123");
        assertThat(request.getAttribute(RequestIdFilter.ATTRIBUTE)).isEqualTo("trace-123");
    }

    @Test
    void generatesThirtyTwoAlphanumericCharactersWhenAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/healthz");
        request.addHeader(RequestIdFilter.HEADER, "");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestIdFilter.HEADER)).matches("[A-Za-z0-9]{32}");
    }

    @Test
    void generatedIdsDiffer() {
        assertThat(filter.generate()).isNotEqualTo(filter.generate());
    }
}
