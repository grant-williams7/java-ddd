package com.example.marketplace.interfaces.api.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(OutputCaptureExtension.class)
class RequestLogFilterTest {

    private final RequestLogFilter filter = new RequestLogFilter();

    @Test
    void logsOneLinePerRequest(CapturedOutput output) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/products");
        request.setQueryString("dry_run=true");
        request.setAttribute(RequestIdFilter.ATTRIBUTE, "trace-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(201);

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(output).containsPattern(
                "INFO .* request method=POST uri=/api/v1/products\\?dry_run=true status=201 "
                        + "latency=\\d+\\.\\dms request_id=trace-123");
    }

    @Test
    void logsFailuresAtErrorLevel(CapturedOutput output) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/boom");
        request.setAttribute(RequestIdFilter.ATTRIBUTE, "trace-456");
        MockFilterChain failingChain = new MockFilterChain(new HttpServlet() {
            @Override
            protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException {
                throw new ServletException("boom");
            }
        });

        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), failingChain))
                .isInstanceOf(ServletException.class);

        assertThat(output).containsPattern(
                "ERROR .* request method=GET uri=/boom status=500 latency=\\S+ request_id=trace-456 "
                        + "error=jakarta.servlet.ServletException: boom");
    }
}
