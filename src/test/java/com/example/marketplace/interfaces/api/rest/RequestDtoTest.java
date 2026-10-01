package com.example.marketplace.interfaces.api.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.marketplace.domain.entities.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RequestDtoTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void createProductRequest_toCreateProductCommand() {
        UUID sellerId = UUID.randomUUID();
        CreateProductRequest request = new CreateProductRequest("key-1", "Widget", 999, "USD", sellerId.toString());

        assertThat(request.toCreateProductCommand()).hasValueSatisfying(command -> {
            assertThat(command.idempotencyKey()).isEqualTo("key-1");
            assertThat(command.name()).isEqualTo("Widget");
            assertThat(command.priceMinorUnits()).isEqualTo(999);
            assertThat(command.currency()).isEqualTo(Currency.USD);
            assertThat(command.sellerId()).isEqualTo(sellerId);
        });
    }

    @Test
    void createProductRequest_toCreateProductCommand_invalidSellerId() {
        CreateProductRequest request = new CreateProductRequest(null, "Widget", 999, "USD", "not-a-uuid");

        assertThat(request.toCreateProductCommand()).isEmpty();
    }

    @Test
    void createProductRequest_jsonTags() {
        UUID sellerId = UUID.randomUUID();
        String body = """
                {"idempotency_key":"key-1","name":"Widget","price_minor_units":1234,"currency":"EUR","seller_id":"%s"}"""
                .formatted(sellerId);

        CreateProductRequest request = mapper.readValue(body, CreateProductRequest.class);

        assertThat(request.idempotencyKey()).isEqualTo("key-1");
        assertThat(request.name()).isEqualTo("Widget");
        assertThat(request.priceMinorUnits()).isEqualTo(1234);
        assertThat(request.currency()).isEqualTo("EUR");
        assertThat(request.sellerId()).isEqualTo(sellerId.toString());
    }

    @Test
    void updateProductRequest_toUpdateProductCommand() {
        UUID sellerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UpdateProductRequest request = new UpdateProductRequest("key-2", "Widget v2", 1999, "EUR", sellerId.toString());

        assertThat(request.toUpdateProductCommand(productId)).hasValueSatisfying(command -> {
            assertThat(command.id()).isEqualTo(productId);
            assertThat(command.idempotencyKey()).isEqualTo("key-2");
            assertThat(command.name()).isEqualTo("Widget v2");
            assertThat(command.priceMinorUnits()).isEqualTo(1999);
            assertThat(command.currency()).isEqualTo(Currency.EUR);
            assertThat(command.sellerId()).isEqualTo(sellerId);
        });
    }

    @Test
    void updateProductRequest_toUpdateProductCommand_invalidSellerId() {
        UpdateProductRequest request = new UpdateProductRequest(null, "Widget", 0, null, "nope");

        assertThat(request.toUpdateProductCommand(UUID.randomUUID())).isEmpty();
    }

    @Test
    void createSellerRequest_toCreateSellerCommand() {
        CreateSellerRequest request = new CreateSellerRequest("key-3", "Acme");

        assertThat(request.toCreateSellerCommand()).satisfies(command -> {
            assertThat(command.idempotencyKey()).isEqualTo("key-3");
            assertThat(command.name()).isEqualTo("Acme");
        });
    }

    @Test
    void updateSellerRequest_toUpdateSellerCommand() {
        UUID id = UUID.randomUUID();
        UpdateSellerRequest request = new UpdateSellerRequest("key-4", id.toString(), "Acme v2");

        assertThat(request.toUpdateSellerCommand()).hasValueSatisfying(command -> {
            assertThat(command.id()).isEqualTo(id);
            assertThat(command.idempotencyKey()).isEqualTo("key-4");
            assertThat(command.name()).isEqualTo("Acme v2");
        });
    }

    @Test
    void updateSellerRequest_jsonTags() {
        UUID id = UUID.randomUUID();
        String body = "{\"idempotency_key\":\"key-4\",\"id\":\"%s\",\"name\":\"Acme v2\"}".formatted(id);

        UpdateSellerRequest request = mapper.readValue(body, UpdateSellerRequest.class);

        assertThat(request.id()).isEqualTo(id.toString());
        assertThat(request.name()).isEqualTo("Acme v2");
    }
}
