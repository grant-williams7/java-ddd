package com.example.marketplace.contract;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** A thin HTTP client for the API under test, configured by {@code CONTRACT_BASE_URL}. */
final class Api {

    static final String BASE_URL = Environment.get("CONTRACT_BASE_URL", "http://localhost:8080");
    static final String JSON = "application/json";

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private Api() {
    }

    record Response(int status, HttpHeaders headers, String body) {

        JsonNode json() {
            return MAPPER.readTree(body);
        }

        String error() {
            return json().get("error").stringValue();
        }

        String header(String name) {
            return headers.firstValue(name).orElse(null);
        }
    }

    static Response get(String path, String... headers) {
        return send("GET", path, null, null, headers);
    }

    static Response delete(String path, String... headers) {
        return send("DELETE", path, null, null, headers);
    }

    static Response postJson(String path, String body, String... headers) {
        return send("POST", path, JSON, body, headers);
    }

    static Response putJson(String path, String body, String... headers) {
        return send("PUT", path, JSON, body, headers);
    }

    /** {@code headers} are name/value pairs. A null body sends no body at all. */
    static Response send(String method, String path, String contentType, String body, String... headers) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(30))
                .method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        if (contentType != null) {
            request.header("Content-Type", contentType);
        }
        for (int i = 0; i < headers.length; i += 2) {
            request.header(headers[i], headers[i + 1]);
        }
        try {
            HttpResponse<String> response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.headers(), response.body());
        } catch (IOException e) {
            throw new IllegalStateException(method + " " + path + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(method + " " + path + " interrupted", e);
        }
    }

    static String unique(String prefix) {
        return prefix + " " + UUID.randomUUID();
    }

    static JsonNode createSeller(String name) {
        Response response = postJson("/api/v1/sellers", "{\"name\":" + quote(name) + "}");
        if (response.status() != 201) {
            throw new IllegalStateException("seller setup failed: " + response.status() + " " + response.body());
        }
        return response.json();
    }

    static JsonNode createProduct(String sellerId, String name, long priceMinorUnits, String currency) {
        Response response = postJson("/api/v1/products", productBody(sellerId, name, priceMinorUnits, currency));
        if (response.status() != 201) {
            throw new IllegalStateException("product setup failed: " + response.status() + " " + response.body());
        }
        return response.json();
    }

    static String productBody(String sellerId, String name, long priceMinorUnits, String currency) {
        return "{\"name\":%s,\"price_minor_units\":%d,\"currency\":%s,\"seller_id\":%s}"
                .formatted(quote(name), priceMinorUnits, quote(currency), quote(sellerId));
    }

    static String quote(String value) {
        return MAPPER.writeValueAsString(value);
    }
}
