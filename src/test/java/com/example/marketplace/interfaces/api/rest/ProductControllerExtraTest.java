package com.example.marketplace.interfaces.api.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.marketplace.application.command.DeleteProductCommandResult;
import com.example.marketplace.application.command.UpdateProductCommandResult;
import com.example.marketplace.application.common.ProductResult;
import com.example.marketplace.application.interfaces.ProductService;
import com.example.marketplace.domain.entities.Currency;
import com.example.marketplace.domain.entities.Money;
import java.util.Optional;
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
class ProductControllerExtraTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ProductService service;

    @Test
    void createProduct_serviceError() {
        when(service.createProduct(any())).thenThrow(new IllegalStateException("boom"));

        assertThat(mvc.post().uri("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"X","price_minor_units":100,"currency":"EUR","seller_id":"%s"}"""
                        .formatted(UUID.randomUUID())))
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);

        verify(service).createProduct(any());
    }

    @Test
    void createProduct_invalidSellerId() {
        assertThat(mvc.post().uri("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"X","price_minor_units":100,"currency":"EUR","seller_id":"not-a-uuid"}"""))
                .hasStatus(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(service);
    }

    @Test
    void getProductById_notFound() {
        UUID id = UUID.randomUUID();
        when(service.findProductById(any())).thenReturn(Optional.empty());

        assertThat(mvc.get().uri("/api/v1/products/{id}", id)).hasStatus(HttpStatus.NOT_FOUND);

        verify(service).findProductById(argThat(query -> query.id().equals(id)));
    }

    @Test
    void getProductById_invalidId() {
        assertThat(mvc.get().uri("/api/v1/products/not-a-uuid")).hasStatus(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(service);
    }

    @Test
    void updateProduct_success() {
        UUID id = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        when(service.updateProduct(argThat(command -> command.id().equals(id)
                && command.priceMinorUnits() == 1999
                && command.currency().equals(Currency.USD)
                && command.sellerId().equals(sellerId))))
                .thenReturn(new UpdateProductCommandResult(new ProductResult(
                        id, "Widget v2", new Money(1999, Currency.USD), sellerId, null, null)));

        assertThat(mvc.put().uri("/api/v1/products/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Widget v2","price_minor_units":1999,"currency":"USD","seller_id":"%s"}"""
                        .formatted(sellerId)))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"name":"Widget v2","price_minor_units":1999,"currency":"USD","seller_id":"%s"}"""
                        .formatted(sellerId));
    }

    @Test
    void deleteProduct_success() {
        UUID id = UUID.randomUUID();
        when(service.deleteProduct(any())).thenReturn(new DeleteProductCommandResult(true));

        assertThat(mvc.delete().uri("/api/v1/products/{id}", id)).hasStatus(HttpStatus.NO_CONTENT);

        verify(service).deleteProduct(argThat(command -> command.id().equals(id)));
    }
}
