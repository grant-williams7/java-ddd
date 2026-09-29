package com.example.marketplace.interfaces.api.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.marketplace.application.command.CreateSellerCommandResult;
import com.example.marketplace.application.common.SellerResult;
import com.example.marketplace.application.interfaces.ProductService;
import com.example.marketplace.application.interfaces.SellerService;
import com.example.marketplace.application.services.IdempotencyKeyReuseException;
import com.example.marketplace.application.services.RequestInFlightException;
import com.example.marketplace.domain.entities.ProductNotFoundException;
import com.example.marketplace.domain.entities.SellerNotFoundException;
import com.example.marketplace.domain.entities.ValidationException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest({ProductController.class, SellerController.class})
@Import(JsonStrictnessConfiguration.class)
class RestErrorsTest {

    private static final String PARSE_ERROR = "{\"error\":\"Failed to parse request body\"}";
    private static final String SELLER_ID = "123e4567-e89b-12d3-a456-426614174000";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ProductService products;

    @MockitoBean
    private SellerService sellers;

    private MvcTestResult postProduct(String body) {
        return mvc.post().uri("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"name\":",
            "",
            "[]",
            "{\"name\":\"W\",\"price_minor_units\":49.99,\"currency\":\"EUR\",\"seller_id\":\"" + SELLER_ID + "\"}",
            "{\"name\":\"W\",\"price_minor_units\":\"4999\",\"currency\":\"EUR\",\"seller_id\":\"" + SELLER_ID + "\"}",
            "{\"name\":\"W\",\"price_minor_units\":\"\",\"currency\":\"EUR\",\"seller_id\":\"" + SELLER_ID + "\"}",
            "{\"name\":\"W\",\"price_minor_units\":null,\"currency\":\"EUR\",\"seller_id\":\"" + SELLER_ID + "\"}",
            "{\"name\":5,\"price_minor_units\":4999,\"currency\":\"EUR\",\"seller_id\":\"" + SELLER_ID + "\"}",
            "{\"name\":{},\"price_minor_units\":4999,\"currency\":\"EUR\",\"seller_id\":\"" + SELLER_ID + "\"}",
    })
    void unreadableBody_isBadRequest(String body) {
        assertThat(postProduct(body)).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().isStrictlyEqualTo(PARSE_ERROR);

        verifyNoInteractions(products);
    }

    @Test
    void nonJsonContentType_isUnsupportedMediaType() {
        assertThat(mvc.post().uri("/api/v1/sellers").contentType(MediaType.TEXT_PLAIN).content("name=x"))
                .hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void jsonWithCharset_isAccepted() {
        when(sellers.createSeller(any())).thenReturn(new CreateSellerCommandResult(
                new SellerResult(UUID.randomUUID(), "Acme", null, null)));

        assertThat(mvc.post().uri("/api/v1/sellers")
                .contentType("application/json;charset=UTF-8")
                .content("{\"name\":\"Acme\"}"))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void unknownFields_areIgnored() {
        when(sellers.createSeller(any())).thenReturn(new CreateSellerCommandResult(
                new SellerResult(UUID.randomUUID(), "Acme", null, null)));

        assertThat(mvc.post().uri("/api/v1/sellers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Acme\",\"nickname\":\"ACME\"}"))
                .hasStatus(HttpStatus.CREATED);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1-1-1-1-1",
            "{123e4567-e89b-12d3-a456-426614174000}",
            "urn:uuid:123e4567-e89b-12d3-a456-426614174000",
            "123e4567e89b12d3a456426614174000",
    })
    void nonCanonicalPathIds_areRejectedWithTheControllersMessage(String id) {
        assertThat(mvc.get().uri("/api/v1/products/{id}", id))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().isStrictlyEqualTo("{\"error\":\"Invalid product Id format\"}");
        assertThat(mvc.get().uri("/api/v1/sellers/{id}", id))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().isStrictlyEqualTo("{\"error\":\"Invalid seller Id format\"}");
        assertThat(mvc.delete().uri("/api/v1/sellers/{id}", id))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().isStrictlyEqualTo("{\"error\":\"Invalid seller Id format\"}");

        verifyNoInteractions(products, sellers);
    }

    @Test
    void upperCasePathIds_areAccepted() {
        when(products.findProductById(any())).thenReturn(Optional.empty());

        assertThat(mvc.get().uri("/api/v1/products/{id}", SELLER_ID.toUpperCase())).hasStatus(HttpStatus.NOT_FOUND);

        verify(products).findProductById(argThat(query -> query.id().toString().equals(SELLER_ID)));
    }

    /** The path id is checked before the body is read, as in the Go version. */
    @Test
    void updateProduct_badPathIdWinsOverBadBody() {
        assertThat(mvc.put().uri("/api/v1/products/nope").contentType(MediaType.APPLICATION_JSON).content("{bad"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().isStrictlyEqualTo("{\"error\":\"Invalid product Id format\"}");
    }

    @Test
    void updateProduct_badSellerId() {
        assertThat(mvc.put().uri("/api/v1/products/{id}", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"W\",\"price_minor_units\":1,\"currency\":\"EUR\",\"seller_id\":\"nope\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().isStrictlyEqualTo("{\"error\":\"Invalid seller Id format\"}");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"name\":\"x\"}",
            "{\"id\":null,\"name\":\"x\"}",
            "{\"id\":\"not-a-uuid\",\"name\":\"x\"}",
            "{\"id\":42,\"name\":\"x\"}",
    })
    void putSeller_missingOrBadId_isUnreadable(String body) {
        assertThat(mvc.put().uri("/api/v1/sellers").contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().isStrictlyEqualTo(PARSE_ERROR);

        verifyNoInteractions(sellers);
    }

    static Stream<Arguments> serviceErrors() {
        return Stream.of(
                Arguments.of(new ProductNotFoundException(), HttpStatus.NOT_FOUND, "product not found"),
                Arguments.of(new SellerNotFoundException(), HttpStatus.NOT_FOUND, "seller not found"),
                Arguments.of(new ValidationException("name must not be empty"), HttpStatus.BAD_REQUEST,
                        "validation failed: name must not be empty"),
                Arguments.of(new RequestInFlightException(), HttpStatus.CONFLICT,
                        "a request with this idempotency key is already in progress"),
                Arguments.of(new IdempotencyKeyReuseException(), HttpStatus.UNPROCESSABLE_CONTENT,
                        "idempotency key was already used with a different request"),
                Arguments.of(new IllegalStateException("connection refused"), HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to create product"));
    }

    @ParameterizedTest
    @MethodSource
    void serviceErrors(RuntimeException error, HttpStatus status, String message) {
        when(products.createProduct(any())).thenThrow(error);

        assertThat(postProduct(
                "{\"name\":\"W\",\"price_minor_units\":1,\"currency\":\"EUR\",\"seller_id\":\"" + SELLER_ID + "\"}"))
                .hasStatus(status)
                .bodyJson().isStrictlyEqualTo("{\"error\":\"" + message + "\"}");
    }

    @Test
    void idempotencyHeader_winsOverBodyKey() {
        when(sellers.createSeller(any())).thenReturn(new CreateSellerCommandResult(
                new SellerResult(UUID.randomUUID(), "Acme", null, null)));

        assertThat(mvc.post().uri("/api/v1/sellers")
                .header(IdempotencyKeys.HEADER, "from-header")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idempotency_key\":\"from-body\",\"name\":\"Acme\"}"))
                .hasStatus(HttpStatus.CREATED);

        verify(sellers).createSeller(argThat(command -> command.idempotencyKey().equals("from-header")));
    }

    @Test
    void deleteUsesOnlyTheHeader() {
        UUID id = UUID.randomUUID();

        assertThat(mvc.delete().uri("/api/v1/sellers/{id}", id).header(IdempotencyKeys.HEADER, "del-key"))
                .hasStatus(HttpStatus.NO_CONTENT);

        verify(sellers).deleteSeller(argThat(command -> command.idempotencyKey().equals("del-key")));
    }
}
