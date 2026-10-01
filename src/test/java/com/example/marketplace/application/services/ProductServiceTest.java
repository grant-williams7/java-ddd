package com.example.marketplace.application.services;

import static com.example.marketplace.application.services.ServiceTestSupport.createProductCommand;
import static com.example.marketplace.application.services.ServiceTestSupport.persistedSeller;
import static com.example.marketplace.application.services.ServiceTestSupport.productService;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.marketplace.application.command.CreateProductCommandResult;
import com.example.marketplace.application.query.GetAllProductsQueryResult;
import com.example.marketplace.application.query.GetProductByIdQuery;
import com.example.marketplace.domain.entities.ValidatedSeller;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductServiceTest {

    private final FakeProductRepository products = new FakeProductRepository();
    private final FakeSellerRepository sellers = new FakeSellerRepository();
    private final DefaultProductService service = productService(products, sellers);

    @Test
    void createProduct() {
        ValidatedSeller seller = persistedSeller(sellers);

        service.createProduct(createProductCommand("Example", 10000, seller.seller().getId()));

        assertThat(products.products).hasSize(1);
    }

    @Test
    void getAllProducts() {
        ValidatedSeller seller = persistedSeller(sellers);
        service.createProduct(createProductCommand("Example1", 10000, seller.seller().getId()));
        service.createProduct(createProductCommand("Example2", 20000, seller.seller().getId()));

        GetAllProductsQueryResult result = service.findAllProducts();

        assertThat(result.result()).hasSize(2);
    }

    @Test
    void findProductById() {
        ValidatedSeller seller = persistedSeller(sellers);
        CreateProductCommandResult created =
                service.createProduct(createProductCommand("Example", 10000, seller.seller().getId()));

        assertThat(service.findProductById(new GetProductByIdQuery(created.result().id())))
                .hasValueSatisfying(found -> assertThat(found.result().name()).isEqualTo("Example"));

        assertThat(service.findProductById(new GetProductByIdQuery(UUID.randomUUID()))).isEmpty();
    }
}
