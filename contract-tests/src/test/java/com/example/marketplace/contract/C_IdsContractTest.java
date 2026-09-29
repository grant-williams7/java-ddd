package com.example.marketplace.contract;

import static com.example.marketplace.contract.Api.createProduct;
import static com.example.marketplace.contract.Api.createSeller;
import static com.example.marketplace.contract.Api.get;
import static com.example.marketplace.contract.Api.unique;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Order(3)
class C_IdsContractTest {

    private static String productId;
    private static String sellerId;

    @BeforeAll
    static void createData() {
        sellerId = createSeller(unique("Seller")).get("id").stringValue();
        productId = createProduct(sellerId, unique("Widget"), 100, "EUR").get("id").stringValue();
    }

    @Test
    void canonical_ids_are_accepted_in_either_case() {
        assertThat(get("/api/v1/products/" + productId.toUpperCase(Locale.ROOT)).status()).isEqualTo(200);
        assertThat(get("/api/v1/sellers/" + sellerId.toUpperCase(Locale.ROOT)).status()).isEqualTo(200);
    }

    @Test
    void responses_use_lower_case_ids() {
        Api.Response response = get("/api/v1/products/" + productId.toUpperCase(Locale.ROOT));

        assertThat(response.json().get("id").stringValue()).isEqualTo(productId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "1-1-1-1-1", "123"})
    void invalid_ids_get_the_controllers_message(String id) {
        Api.Response product = get("/api/v1/products/" + id);
        Api.Response seller = get("/api/v1/sellers/" + id);

        assertThat(product.status()).isEqualTo(400);
        assertThat(product.error()).isEqualTo("Invalid product Id format");
        assertThat(seller.status()).isEqualTo(400);
        assertThat(seller.error()).isEqualTo("Invalid seller Id format");
    }

    @ChangedFromGo("§2.3 item 3: the spec's format: uuid is the canonical form; Go also accepted braces, urn:uuid: and 32 hex digits")
    @Test
    void non_canonical_uuid_forms_are_rejected() {
        String hex = productId.replace("-", "");

        for (String id : new String[] {"%7B" + productId + "%7D", "urn:uuid:" + productId, hex}) {
            Api.Response response = get("/api/v1/products/" + id);
            assertThat(response.status()).as(id).isEqualTo(400);
            assertThat(response.error()).as(id).isEqualTo("Invalid product Id format");
        }
    }
}
