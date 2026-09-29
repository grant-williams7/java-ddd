package com.example.marketplace.bootstrap;

import com.example.marketplace.application.interfaces.ProductService;
import com.example.marketplace.application.interfaces.SellerService;
import com.example.marketplace.application.services.ApplicationServices;
import com.example.marketplace.application.services.ResultCodec;
import com.example.marketplace.domain.repositories.IdempotencyRepository;
import com.example.marketplace.domain.repositories.ProductRepository;
import com.example.marketplace.domain.repositories.SellerRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one place where the layers meet. The application layer carries no
 * Spring annotations, so its services become beans here, built from the
 * repositories and codec that infrastructure contributes by type.
 */
@Configuration(proxyBeanMethods = false)
class ApplicationWiring {

    @Bean
    ProductService productService(ProductRepository productRepository, SellerRepository sellerRepository,
            IdempotencyRepository idempotencyRepository, ResultCodec codec) {
        return ApplicationServices.productService(productRepository, sellerRepository, idempotencyRepository, codec);
    }

    @Bean
    SellerService sellerService(SellerRepository sellerRepository, IdempotencyRepository idempotencyRepository,
            ResultCodec codec) {
        return ApplicationServices.sellerService(sellerRepository, idempotencyRepository, codec);
    }
}
