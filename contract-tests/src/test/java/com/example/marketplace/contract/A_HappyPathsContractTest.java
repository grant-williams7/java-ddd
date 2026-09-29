package com.example.marketplace.contract;

import static com.example.marketplace.contract.Api.createProduct;
import static com.example.marketplace.contract.Api.createSeller;
import static com.example.marketplace.contract.Api.delete;
import static com.example.marketplace.contract.Api.get;
import static com.example.marketplace.contract.Api.postJson;
import static com.example.marketplace.contract.Api.productBody;
import static com.example.marketplace.contract.Api.putJson;
import static com.example.marketplace.contract.Api.unique;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

@Order(1)
class A_HappyPathsContractTest {

    static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    static final String UTC_TIMESTAMP = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?Z";

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.propertyNames().forEach(names::add);
        return names;
    }

    @Test
    void create_seller_returns_201_with_the_documented_fields() {
        Api.Response response = postJson("/api/v1/sellers", "{\"name\":\"Acme\"}");

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.header("Content-Type")).startsWith("application/json");
        JsonNode seller = response.json();
        assertThat(fieldNames(seller)).containsExactly("id", "name", "created_at", "updated_at");
        assertThat(seller.get("id").stringValue()).matches(UUID_PATTERN);
        assertThat(seller.get("name").stringValue()).isEqualTo("Acme");
        assertThat(seller.get("created_at").stringValue()).matches(UTC_TIMESTAMP);
        assertThat(seller.get("updated_at").stringValue()).matches(UTC_TIMESTAMP);
    }

    @Test
    void get_seller_by_id() {
        JsonNode created = createSeller(unique("Seller"));

        Api.Response response = get("/api/v1/sellers/" + created.get("id").stringValue());

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().get("id")).isEqualTo(created.get("id"));
        assertThat(response.json().get("name")).isEqualTo(created.get("name"));
    }

    @Test
    void list_sellers_newest_first() throws InterruptedException {
        Database.truncate();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            names.add(createSeller(unique("Seller " + i)).get("name").stringValue());
            Thread.sleep(5);
        }

        Api.Response response = get("/api/v1/sellers");

        assertThat(response.status()).isEqualTo(200);
        List<String> listed = new ArrayList<>();
        response.json().get("sellers").forEach(seller -> listed.add(seller.get("name").stringValue()));
        assertThat(listed).containsExactly(names.get(2), names.get(1), names.get(0));
    }

    @Test
    void update_seller_with_the_id_in_the_body() {
        JsonNode created = createSeller(unique("Seller"));
        String id = created.get("id").stringValue();

        Api.Response response = putJson("/api/v1/sellers", "{\"id\":\"%s\",\"name\":\"Renamed\"}".formatted(id));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().get("id").stringValue()).isEqualTo(id);
        assertThat(response.json().get("name").stringValue()).isEqualTo("Renamed");
        assertThat(get("/api/v1/sellers/" + id).json().get("name").stringValue()).isEqualTo("Renamed");
    }

    @Test
    void delete_seller_returns_204_without_a_body() {
        String id = createSeller(unique("Seller")).get("id").stringValue();

        Api.Response response = delete("/api/v1/sellers/" + id);

        assertThat(response.status()).isEqualTo(204);
        assertThat(response.body()).isEmpty();
        assertThat(get("/api/v1/sellers/" + id).status()).isEqualTo(404);
    }

    @Test
    void create_product_returns_201_with_the_documented_fields_in_order() {
        String sellerId = createSeller(unique("Seller")).get("id").stringValue();

        Api.Response response = postJson("/api/v1/products", productBody(sellerId, "Widget", 4999, "EUR"));

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.header("Content-Type")).startsWith("application/json");
        JsonNode product = response.json();
        assertThat(fieldNames(product)).containsExactly(
                "id", "name", "price_minor_units", "currency", "seller_id", "created_at", "updated_at");
        assertThat(product.get("id").stringValue()).matches(UUID_PATTERN);
        assertThat(product.get("name").stringValue()).isEqualTo("Widget");
        assertThat(product.get("price_minor_units").isIntegralNumber()).isTrue();
        assertThat(product.get("price_minor_units").longValue()).isEqualTo(4999);
        assertThat(product.get("currency").stringValue()).isEqualTo("EUR");
        assertThat(product.get("seller_id").stringValue()).isEqualTo(sellerId);
        assertThat(product.get("created_at").stringValue()).matches(UTC_TIMESTAMP);
        assertThat(product.get("updated_at").stringValue()).matches(UTC_TIMESTAMP);
    }

    @Test
    void get_product_by_id() {
        String sellerId = createSeller(unique("Seller")).get("id").stringValue();
        JsonNode created = createProduct(sellerId, unique("Widget"), 1299, "USD");

        Api.Response response = get("/api/v1/products/" + created.get("id").stringValue());

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().get("id")).isEqualTo(created.get("id"));
        assertThat(response.json().get("price_minor_units").longValue()).isEqualTo(1299);
        assertThat(response.json().get("currency").stringValue()).isEqualTo("USD");
    }

    @ChangedFromGo("create/update responses carried Go's nanosecond clock; stored rows have microseconds")
    @Test
    void create_response_timestamps_match_a_later_read() {
        String sellerId = createSeller(unique("Seller")).get("id").stringValue();
        JsonNode created = createProduct(sellerId, unique("Widget"), 1299, "USD");

        JsonNode read = get("/api/v1/products/" + created.get("id").stringValue()).json();

        assertThat(read.get("created_at")).isEqualTo(created.get("created_at"));
        assertThat(read.get("updated_at")).isEqualTo(created.get("updated_at"));
    }

    @Test
    void list_products_newest_first() throws InterruptedException {
        Database.truncate();
        String sellerId = createSeller(unique("Seller")).get("id").stringValue();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            names.add(createProduct(sellerId, unique("Widget " + i), 100 + i, "EUR").get("name").stringValue());
            Thread.sleep(5);
        }

        Api.Response response = get("/api/v1/products");

        assertThat(response.status()).isEqualTo(200);
        List<String> listed = new ArrayList<>();
        response.json().get("products").forEach(product -> listed.add(product.get("name").stringValue()));
        assertThat(listed).containsExactly(names.get(2), names.get(1), names.get(0));
    }

    @Test
    void empty_product_list_is_an_empty_array() {
        Database.truncate();

        Api.Response response = get("/api/v1/products");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().toString()).isEqualTo("{\"products\":[]}");
    }

    @ChangedFromGo("migration.md §2.2 item 1: the spec types sellers as an array; Go answered null")
    @Test
    void empty_seller_list_is_an_empty_array() {
        Database.truncate();

        Api.Response response = get("/api/v1/sellers");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().toString()).isEqualTo("{\"sellers\":[]}");
    }

    @Test
    void update_product() {
        String sellerId = createSeller(unique("Seller")).get("id").stringValue();
        String otherSellerId = createSeller(unique("Other Seller")).get("id").stringValue();
        String id = createProduct(sellerId, unique("Widget"), 1000, "EUR").get("id").stringValue();

        Api.Response response = putJson("/api/v1/products/" + id, productBody(otherSellerId, "Widget v2", 1999, "USD"));

        assertThat(response.status()).isEqualTo(200);
        JsonNode product = response.json();
        assertThat(product.get("id").stringValue()).isEqualTo(id);
        assertThat(product.get("name").stringValue()).isEqualTo("Widget v2");
        assertThat(product.get("price_minor_units").longValue()).isEqualTo(1999);
        assertThat(product.get("currency").stringValue()).isEqualTo("USD");
        assertThat(product.get("seller_id").stringValue()).isEqualTo(otherSellerId);
        assertThat(get("/api/v1/products/" + id).json().get("name").stringValue()).isEqualTo("Widget v2");
    }

    @Test
    void delete_product_returns_204_without_a_body() {
        String sellerId = createSeller(unique("Seller")).get("id").stringValue();
        String id = createProduct(sellerId, unique("Widget"), 1000, "EUR").get("id").stringValue();

        Api.Response response = delete("/api/v1/products/" + id);

        assertThat(response.status()).isEqualTo(204);
        assertThat(response.body()).isEmpty();
        assertThat(get("/api/v1/products/" + id).status()).isEqualTo(404);
    }

    @Test
    void liveness_and_readiness() {
        Api.Response liveness = get("/healthz");
        Api.Response readiness = get("/readyz");

        assertThat(liveness.status()).isEqualTo(200);
        assertThat(liveness.json().toString()).isEqualTo("{\"status\":\"ok\"}");
        assertThat(readiness.status()).isEqualTo(200);
        assertThat(readiness.json().toString()).isEqualTo("{\"status\":\"ok\"}");
    }

    @Test
    void unknown_routes_and_methods_use_standard_status_codes() {
        assertThat(get("/api/v1/nothing-here").status()).isEqualTo(404);
        assertThat(Api.send("PATCH", "/api/v1/products", Api.JSON, "{}").status()).isEqualTo(405);
    }
}
