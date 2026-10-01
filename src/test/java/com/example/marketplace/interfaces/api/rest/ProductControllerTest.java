package com.example.marketplace.interfaces.api.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.marketplace.application.command.CreateProductCommand;
import com.example.marketplace.application.command.CreateProductCommandResult;
import com.example.marketplace.application.common.ProductResult;
import com.example.marketplace.application.interfaces.ProductService;
import com.example.marketplace.application.query.GetAllProductsQueryResult;
import com.example.marketplace.domain.entities.Currency;
import com.example.marketplace.domain.entities.Money;
import com.example.marketplace.domain.entities.Uuids;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(ProductController.class)
@Import(JsonStrictnessConfiguration.class)
class ProductControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ProductService service;

    @Test
    void createProduct() {
        String sellerId = "123e4567-e89b-12d3-a456-426614174000";
        when(service.createProduct(argThat(command -> command.name().equals("TestProduct")
                && command.priceMinorUnits() == 999
                && command.currency().equals(Currency.USD)
                && command.sellerId().toString().equals(sellerId)
                && "idem-123".equals(command.idempotencyKey()))))
                .thenAnswer(invocation -> {
                    CreateProductCommand command = invocation.getArgument(0);
                    Instant now = Instant.now();
                    return new CreateProductCommandResult(new ProductResult(Uuids.newV7(), command.name(),
                            new Money(command.priceMinorUnits(), command.currency()), command.sellerId(), now, now));
                });

        assertThat(mvc.post().uri("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"TestProduct","price_minor_units":999,"currency":"USD",
                         "seller_id":"%s","idempotency_key":"idem-123"}""".formatted(sellerId)))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"name":"TestProduct","price_minor_units":999,"currency":"USD","seller_id":"%s"}"""
                        .formatted(sellerId));

        verify(service).createProduct(argThat(command -> command.name().equals("TestProduct")));
    }

    @Test
    void getAllProducts() {
        UUID sellerId = UUID.randomUUID();
        ProductResult first = new ProductResult(UUID.randomUUID(), "TestProduct1", new Money(999, Currency.USD),
                sellerId, null, null);
        ProductResult second = new ProductResult(UUID.randomUUID(), "TestProduct2", new Money(1499, Currency.EUR),
                sellerId, null, null);
        when(service.findAllProducts()).thenReturn(new GetAllProductsQueryResult(List.of(first, second)));

        assertThat(mvc.get().uri("/api/v1/products"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"products":[
                          {"id":"%s","name":"TestProduct1","price_minor_units":999,"currency":"USD","seller_id":"%s"},
                          {"id":"%s","name":"TestProduct2","price_minor_units":1499,"currency":"EUR","seller_id":"%s"}
                        ]}""".formatted(first.id(), sellerId, second.id(), sellerId));
    }

    @Test
    void getAllProducts_emptyReturnsEmptyArray() {
        when(service.findAllProducts()).thenReturn(new GetAllProductsQueryResult(List.of()));

        assertThat(mvc.get().uri("/api/v1/products"))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("{\"products\":[]}");
    }
}
