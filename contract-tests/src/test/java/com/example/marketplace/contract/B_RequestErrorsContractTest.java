package com.example.marketplace.contract;

import static com.example.marketplace.contract.Api.createSeller;
import static com.example.marketplace.contract.Api.postJson;
import static com.example.marketplace.contract.Api.unique;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Order(2)
class B_RequestErrorsContractTest {

    private static final String PARSE_ERROR = "Failed to parse request body";

    private static String sellerId;

    @BeforeAll
    static void createSellerForProducts() {
        sellerId = createSeller(unique("Seller")).get("id").stringValue();
    }

    private static String product(String priceJson) {
        return "{\"name\":\"W\",\"price_minor_units\":%s,\"currency\":\"EUR\",\"seller_id\":\"%s\"}"
                .formatted(priceJson, sellerId);
    }

    @Test
    void malformed_json_is_a_parse_error() {
        Api.Response response = postJson("/api/v1/products", "{\"name\":");

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.error()).isEqualTo(PARSE_ERROR);
    }

    @ParameterizedTest
    @ValueSource(strings = {"49.99", "49.0", "\"4999\"", "1e3"})
    void price_must_be_a_json_integer(String price) {
        Api.Response response = postJson("/api/v1/products", product(price));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.error()).isEqualTo(PARSE_ERROR);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"name\":{}}", "{\"name\":5}", "{\"name\":[\"a\"]}"})
    void wrong_json_types_are_parse_errors(String body) {
        Api.Response response = postJson("/api/v1/sellers", body);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.error()).isEqualTo(PARSE_ERROR);
    }

    @Test
    void unknown_fields_are_ignored() {
        Api.Response response = postJson("/api/v1/sellers", "{\"name\":\"Acme\",\"nickname\":\"ACME\"}");

        assertThat(response.status()).isEqualTo(201);
    }

    @ChangedFromGo("D12: the spec documents only application/json; Go answered 400, Spring answers 415")
    @Test
    void non_json_content_type_is_unsupported() {
        Api.Response response = Api.send("POST", "/api/v1/sellers", "text/plain", "{\"name\":\"Acme\"}");

        assertThat(response.status()).isEqualTo(415);
    }

    @ChangedFromGo("§9: Go bound an empty body as all-zero fields and failed later with a field message")
    @Test
    void empty_body_is_a_parse_error() {
        Api.Response product = postJson("/api/v1/products", "");
        Api.Response seller = postJson("/api/v1/sellers", "");

        assertThat(product.status()).isEqualTo(400);
        assertThat(product.error()).isEqualTo(PARSE_ERROR);
        assertThat(seller.status()).isEqualTo(400);
        assertThat(seller.error()).isEqualTo(PARSE_ERROR);
    }

    @ChangedFromGo("§9: Go read null as zero and failed later with \"price must be greater than 0\"")
    @Test
    void null_price_is_a_parse_error() {
        Api.Response response = postJson("/api/v1/products", product("null"));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.error()).isEqualTo(PARSE_ERROR);
    }

    @ChangedFromGo("§9: Go matched JSON keys case-insensitively")
    @Test
    void json_keys_are_case_sensitive() {
        Api.Response response = postJson("/api/v1/sellers", "{\"NAME\":\"Acme\"}");

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.error()).isEqualTo("validation failed: name must not be empty");
    }

    @ChangedFromGo("§9: Go ignored content after the JSON value")
    @Test
    void trailing_content_is_a_parse_error() {
        Api.Response response = postJson("/api/v1/sellers", "{\"name\":\"Acme\"} trailing");

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.error()).isEqualTo(PARSE_ERROR);
    }
}
