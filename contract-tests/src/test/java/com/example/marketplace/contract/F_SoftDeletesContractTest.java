package com.example.marketplace.contract;

import static com.example.marketplace.contract.Api.createProduct;
import static com.example.marketplace.contract.Api.createSeller;
import static com.example.marketplace.contract.Api.delete;
import static com.example.marketplace.contract.Api.get;
import static com.example.marketplace.contract.Api.productBody;
import static com.example.marketplace.contract.Api.putJson;
import static com.example.marketplace.contract.Api.unique;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

@Order(6)
class F_SoftDeletesContractTest {

    @Test
    void deleting_a_seller_hides_its_products() {
        String sellerId = createSeller(unique("Seller")).get("id").stringValue();
        String productId = createProduct(sellerId, unique("Widget"), 100, "EUR").get("id").stringValue();

        assertThat(delete("/api/v1/sellers/" + sellerId).status()).isEqualTo(204);

        Api.Response read = get("/api/v1/products/" + productId);
        assertThat(read.status()).isEqualTo(404);
        assertThat(read.error()).isEqualTo("Product not found");

        Api.Response update = putJson("/api/v1/products/" + productId, productBody(sellerId, "W", 1, "EUR"));
        assertThat(update.status()).isEqualTo(404);
        assertThat(update.error()).isEqualTo("product not found");

        assertThat(delete("/api/v1/products/" + productId).status()).isEqualTo(404);
        assertThat(get("/api/v1/products").body()).doesNotContain(productId);
    }

    @Test
    void deletes_are_soft() {
        String sellerId = createSeller(unique("Seller")).get("id").stringValue();

        delete("/api/v1/sellers/" + sellerId);

        assertThat(Database.count("SELECT COUNT(*) FROM sellers WHERE id = ? AND deleted_at IS NOT NULL",
                UUID.fromString(sellerId))).isOne();
    }

    @Test
    void a_second_delete_is_not_found() {
        String sellerId = createSeller(unique("Seller")).get("id").stringValue();
        String productId = createProduct(sellerId, unique("Widget"), 100, "EUR").get("id").stringValue();

        delete("/api/v1/products/" + productId);
        Api.Response secondProductDelete = delete("/api/v1/products/" + productId);
        delete("/api/v1/sellers/" + sellerId);
        Api.Response secondSellerDelete = delete("/api/v1/sellers/" + sellerId);

        assertThat(secondProductDelete.status()).isEqualTo(404);
        assertThat(secondProductDelete.error()).isEqualTo("product not found");
        assertThat(secondSellerDelete.status()).isEqualTo(404);
        assertThat(secondSellerDelete.error()).isEqualTo("seller not found");
    }

    @Test
    void updating_a_deleted_row_is_not_found() {
        String sellerId = createSeller(unique("Seller")).get("id").stringValue();
        delete("/api/v1/sellers/" + sellerId);

        Api.Response response = putJson("/api/v1/sellers", "{\"id\":\"%s\",\"name\":\"x\"}".formatted(sellerId));

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.error()).isEqualTo("seller not found");
    }
}
