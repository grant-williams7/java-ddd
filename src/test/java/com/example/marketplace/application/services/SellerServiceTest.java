package com.example.marketplace.application.services;

import static com.example.marketplace.application.services.ServiceTestSupport.createSellerCommand;
import static com.example.marketplace.application.services.ServiceTestSupport.sellerService;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.marketplace.application.command.UpdateSellerCommand;
import com.example.marketplace.application.query.GetSellerByIdQuery;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SellerServiceTest {

    private final FakeSellerRepository sellers = new FakeSellerRepository();
    private final DefaultSellerService service = sellerService(sellers);

    @Test
    void createSeller() {
        service.createSeller(createSellerCommand("John Doe"));

        assertThat(sellers.sellers).hasSize(1);
    }

    @Test
    void getAllSellers() {
        service.createSeller(createSellerCommand("John Doe"));
        service.createSeller(createSellerCommand("Jane Doe"));

        assertThat(service.findAllSellers().result()).hasSize(2);
    }

    @Test
    void getSellerById() {
        UUID sellerId = service.createSeller(createSellerCommand("John Doe")).result().id();

        assertThat(service.findSellerById(new GetSellerByIdQuery(sellerId)))
                .hasValueSatisfying(found -> assertThat(found.result().name()).isEqualTo("John Doe"));

        assertThat(service.findSellerById(new GetSellerByIdQuery(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void updateSeller() {
        UUID sellerId = service.createSeller(createSellerCommand("John Doe")).result().id();

        service.updateSeller(new UpdateSellerCommand("", sellerId, "Doe Johnny"));

        assertThat(service.findSellerById(new GetSellerByIdQuery(sellerId)))
                .hasValueSatisfying(found -> assertThat(found.result().name()).isEqualTo("Doe Johnny"));
    }
}
