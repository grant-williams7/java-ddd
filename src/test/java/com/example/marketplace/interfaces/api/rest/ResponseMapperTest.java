package com.example.marketplace.interfaces.api.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.marketplace.application.common.ProductResult;
import com.example.marketplace.application.common.SellerResult;
import com.example.marketplace.domain.entities.Currency;
import com.example.marketplace.domain.entities.Money;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ResponseMapperTest {

    @Test
    void toSellerResponse() {
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();

        SellerResponse response = SellerResponseMapper.toSellerResponse(new SellerResult(id, "Acme", now, now));

        assertThat(response.id()).isEqualTo(id.toString());
        assertThat(response.name()).isEqualTo("Acme");
        assertThat(response.createdAt()).isEqualTo(now);
    }

    @Test
    void toSellerListResponse() {
        List<SellerResult> results = List.of(
                new SellerResult(UUID.randomUUID(), "Acme", null, null),
                new SellerResult(UUID.randomUUID(), "Globex", null, null));

        ListSellersResponse response = SellerResponseMapper.toSellerListResponse(results);

        assertThat(response.sellers()).extracting(SellerResponse::name).containsExactly("Acme", "Globex");
    }

    /** Go returned {@code null} here; the spec types {@code sellers} as an array. */
    @Test
    void toSellerListResponse_empty() {
        ListSellersResponse response = SellerResponseMapper.toSellerListResponse(null);

        assertThat(response.sellers()).isNotNull().isEmpty();
    }

    @Test
    void toProductResponse() {
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();

        ProductResponse response = ProductResponseMapper.toProductResponse(
                new ProductResult(id, "Widget", new Money(999, Currency.USD), sellerId, now, now));

        assertThat(response.id()).isEqualTo(id.toString());
        assertThat(response.name()).isEqualTo("Widget");
        assertThat(response.priceMinorUnits()).isEqualTo(999);
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.sellerId()).isEqualTo(sellerId.toString());
        assertThat(response.createdAt()).isEqualTo(now);
    }

    @Test
    void toProductResponse_jsonShape() {
        ProductResult result = new ProductResult(UUID.randomUUID(), "Widget", new Money(1234, Currency.EUR),
                UUID.randomUUID(), null, null);

        JsonMapper mapper = JsonMapper.builder().build();
        JsonNode payload = mapper.readTree(mapper.writeValueAsString(ProductResponseMapper.toProductResponse(result)));

        assertThat(payload.get("price_minor_units").longValue()).isEqualTo(1234);
        assertThat(payload.get("currency").stringValue()).isEqualTo("EUR");
        assertThat(payload.get("seller_id").stringValue()).isEqualTo(result.sellerId().toString());
    }

    @Test
    void toProductListResponse() {
        List<ProductResult> results = List.of(
                new ProductResult(UUID.randomUUID(), "Widget", new Money(100, Currency.USD), UUID.randomUUID(), null, null),
                new ProductResult(UUID.randomUUID(), "Gadget", new Money(200, Currency.EUR), UUID.randomUUID(), null, null));

        ListProductsResponse response = ProductResponseMapper.toProductListResponse(results);

        assertThat(response.products()).hasSize(2);
        assertThat(response.products().get(0).name()).isEqualTo("Widget");
        assertThat(response.products().get(0).priceMinorUnits()).isEqualTo(100);
        assertThat(response.products().get(1).name()).isEqualTo("Gadget");
        assertThat(response.products().get(1).currency()).isEqualTo("EUR");
    }

    @Test
    void toProductListResponse_empty() {
        ListProductsResponse response = ProductResponseMapper.toProductListResponse(null);

        assertThat(response.products()).isNotNull().isEmpty();
    }
}
