package com.example.marketplace.interfaces.api.rest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Echoes the caller's {@code X-Request-Id}, or generates one, and puts it on
 * every response so a request can be traced through the logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Request-Id";
    static final String ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int GENERATED_LENGTH = 32;

    private final SecureRandom random = new SecureRandom();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader(HEADER);
        if (requestId == null || requestId.isEmpty()) {
            requestId = generate();
        }

        request.setAttribute(ATTRIBUTE, requestId);
        response.setHeader(HEADER, requestId);

        chain.doFilter(request, response);
    }

    String generate() {
        StringBuilder id = new StringBuilder(GENERATED_LENGTH);
        for (int i = 0; i < GENERATED_LENGTH; i++) {
            id.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return id.toString();
    }
}
