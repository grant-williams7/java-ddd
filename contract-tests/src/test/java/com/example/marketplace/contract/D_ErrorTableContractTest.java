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

import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/** Every row of migration.md §4.4, plus the check-order cases of §4.7. */
@Order(4)
class D_ErrorTableContractTest {

    private static String sellerId;
    private static String productId;

    @BeforeAll
    static void createData() {
        sellerId = createSeller(unique("Seller")).get("id").stringValue();
        productId = createProduct(sellerId, unique("Widget"), 100, "EUR").get("id").stringValue();
    }

    private static void assertError(Api.Response response, int status, String message) {
        assertThat(response.status()).as(response.body()).isEqualTo(status);
        assertThat(response.error()).isEqualTo(message);
    }

    @Test
    void post_products() {
        assertError(postJson("/api/v1/products", productBody("nope", "W", 100, "EUR")),
                400, "Invalid product Id format");
        assertError(postJson("/api/v1/products", "{\"name\":\"W\",\"price_minor_units\":100,\"currency\":\"EUR\"}"),
                400, "Invalid product Id format");
        assertError(postJson("/api/v1/products", productBody(UUID.randomUUID().toString(), "W", 100, "EUR")),
                404, "seller not found");
        assertError(postJson("/api/v1/products", productBody(sellerId, "W", 100, "XYZ")),
                400, "validation failed: unsupported currency \"XYZ\"");
        assertError(postJson("/api/v1/products", productBody(sellerId, "W", -1, "EUR")),
                400, "validation failed: amount must not be negative");
        assertError(postJson("/api/v1/products", productBody(sellerId, "W", 0, "EUR")),
                400, "validation failed: price must be greater than 0");
        assertError(postJson("/api/v1/products", productBody(sellerId, "", 100, "EUR")),
                400, "validation failed: name must not be empty");
    }

    @Test
    void get_product() {
        assertError(get("/api/v1/products/nope"), 400, "Invalid product Id format");
        assertError(get("/api/v1/products/" + UUID.randomUUID()), 404, "Product not found");
    }

    @Test
    void put_product() {
        assertError(putJson("/api/v1/products/nope", productBody(sellerId, "W", 100, "EUR")),
                400, "Invalid product Id format");
        assertError(putJson("/api/v1/products/" + productId, "{\"name\":"), 400, "Failed to parse request body");
        assertError(putJson("/api/v1/products/" + productId, productBody("nope", "W", 100, "EUR")),
                400, "Invalid seller Id format");
        assertError(putJson("/api/v1/products/" + UUID.randomUUID(), productBody(sellerId, "W", 100, "EUR")),
                404, "product not found");
        assertError(putJson("/api/v1/products/" + productId, productBody(sellerId, "W", 100, "XYZ")),
                400, "validation failed: unsupported currency \"XYZ\"");
    }

    @Test
    void delete_product() {
        assertError(delete("/api/v1/products/nope"), 400, "Invalid product Id format");
        assertError(delete("/api/v1/products/" + UUID.randomUUID()), 404, "product not found");
    }

    @Test
    void post_sellers() {
        assertError(postJson("/api/v1/sellers", "{\"name\":"), 400, "Failed to parse request body");
        assertError(postJson("/api/v1/sellers", "{\"name\":\"\"}"), 400, "validation failed: name must not be empty");
    }

    @Test
    void get_seller() {
        assertError(get("/api/v1/sellers/nope"), 400, "Invalid seller Id format");
        assertError(get("/api/v1/sellers/" + UUID.randomUUID()), 404, "Seller not found");
    }

    @Test
    void put_sellers() {
        assertError(putJson("/api/v1/sellers", "{\"id\":\"nope\",\"name\":\"x\"}"), 400, "Failed to parse request body");
        assertError(putJson("/api/v1/sellers", "{\"id\":"), 400, "Failed to parse request body");
        assertError(putJson("/api/v1/sellers", "{\"id\":\"%s\",\"name\":\"x\"}".formatted(UUID.randomUUID())),
                404, "seller not found");
        assertError(putJson("/api/v1/sellers", "{\"id\":\"%s\",\"name\":\"\"}".formatted(sellerId)),
                400, "validation failed: name must not be empty");
    }

    @ChangedFromGo("§2.2 item 2: the spec requires id; Go read a missing or null id as the nil UUID and answered 404")
    @Test
    void put_sellers_without_an_id_is_a_parse_error() {
        assertError(putJson("/api/v1/sellers", "{\"name\":\"x\"}"), 400, "Failed to parse request body");
        assertError(putJson("/api/v1/sellers", "{\"id\":null,\"name\":\"x\"}"), 400, "Failed to parse request body");
    }

    @Test
    void delete_seller() {
        assertError(delete("/api/v1/sellers/nope"), 400, "Invalid seller Id format");
        assertError(delete("/api/v1/sellers/" + UUID.randomUUID()), 404, "seller not found");
    }

    @Test
    void create_looks_up_the_seller_before_validating_the_price() {
        assertError(postJson("/api/v1/products", productBody(UUID.randomUUID().toString(), "W", 100, "XYZ")),
                404, "seller not found");
    }

    @Test
    void update_validates_the_name_before_the_price() {
        assertError(putJson("/api/v1/products/" + productId, productBody(sellerId, "", 100, "XYZ")),
                400, "validation failed: name must not be empty");
    }
}
