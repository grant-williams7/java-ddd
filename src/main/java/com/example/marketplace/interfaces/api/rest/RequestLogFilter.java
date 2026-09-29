package com.example.marketplace.interfaces.api.rest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** One log line per request; at ERROR when the request failed with an exception. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class RequestLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

    private static final String MESSAGE = "request method={} uri={} status={} latency={} request_id={}";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException e) {
            log.error(MESSAGE + " error={}", request.getMethod(), uri(request),
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR, latency(start), requestId(request), e.toString());
            throw e;
        }

        log.info(MESSAGE, request.getMethod(), uri(request), response.getStatus(), latency(start), requestId(request));
    }

    private static String uri(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
    }

    private static String latency(long startNanos) {
        return String.format(Locale.ROOT, "%.1fms", (System.nanoTime() - startNanos) / 1_000_000.0);
    }

    private static Object requestId(HttpServletRequest request) {
        return request.getAttribute(RequestIdFilter.ATTRIBUTE);
    }
}
