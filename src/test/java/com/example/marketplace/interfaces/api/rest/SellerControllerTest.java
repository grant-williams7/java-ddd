package com.example.marketplace.interfaces.api.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.marketplace.application.command.CreateSellerCommand;
import com.example.marketplace.application.common.SellerResult;
import com.example.marketplace.application.interfaces.SellerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(SellerController.class)
@Import(JsonStrictnessConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SellerControllerTest {

    @TestConfiguration
    static class FakeService {

        @Bean
        FakeSellerService sellerService() {
            return new FakeSellerService();
        }
    }

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private SellerService service;

    private SellerResult existingSeller() {
        return service.createSeller(new CreateSellerCommand("", "TestSeller")).result();
    }

    @Test
    void createSeller() {
        assertThat(mvc.post().uri("/api/v1/sellers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"TestSeller\"}"))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .satisfies(body -> {
                    assertThat(body).extractingPath("$.name").isEqualTo("TestSeller");
                    assertThat(body).extractingPath("$.id").asString().isNotEmpty();
                });
    }

    @Test
    void putSeller() {
        SellerResult seller = existingSeller();

        assertThat(mvc.put().uri("/api/v1/sellers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"%s\",\"name\":\"updatedName\"}".formatted(seller.id())))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"id\":\"%s\",\"name\":\"updatedName\"}".formatted(seller.id()));
    }

    /** The spec requires {@code id}. Go treated a missing one as the nil UUID and answered 404. */
    @Test
    void putSeller_missingId_isRejected() {
        existingSeller();

        assertThat(mvc.put().uri("/api/v1/sellers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"updatedName\"}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .isStrictlyEqualTo("{\"error\":\"Failed to parse request body\"}");
    }

    @Test
    void deleteSeller() {
        SellerResult seller = existingSeller();

        assertThat(mvc.delete().uri("/api/v1/sellers/{id}", seller.id())).hasStatus(HttpStatus.NO_CONTENT);
    }

    @Test
    void getSellerById() {
        SellerResult seller = existingSeller();

        assertThat(mvc.get().uri("/api/v1/sellers/{id}", seller.id()))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("{\"id\":\"%s\",\"name\":\"%s\"}".formatted(seller.id(), seller.name()));
    }

    @Test
    void getAllSellers() {
        service.createSeller(new CreateSellerCommand("", "TestSeller1"));
        service.createSeller(new CreateSellerCommand("", "TestSeller2"));

        assertThat(mvc.get().uri("/api/v1/sellers"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.sellers")
                .asArray()
                .hasSize(2);
    }
}
