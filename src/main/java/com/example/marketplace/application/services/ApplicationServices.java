package com.example.marketplace.application.services;

import com.example.marketplace.application.interfaces.ProductService;
import com.example.marketplace.application.interfaces.SellerService;
import com.example.marketplace.domain.repositories.IdempotencyRepository;
import com.example.marketplace.domain.repositories.ProductRepository;
import com.example.marketplace.domain.repositories.SellerRepository;

/**
 * The only way to obtain the application services. The implementations are
 * package-private, so callers depend on the service interfaces alone.
 */
public final class ApplicationServices {

    private ApplicationServices() {
    }

    public static ProductService productService(ProductRepository productRepository,
            SellerRepository sellerRepository, IdempotencyRepository idempotencyRepository, ResultCodec codec) {
        return new DefaultProductService(productRepository, sellerRepository,
                new Idempotency(idempotencyRepository, codec));
    }

    public static SellerService sellerService(SellerRepository sellerRepository,
            IdempotencyRepository idempotencyRepository, ResultCodec codec) {
        return new DefaultSellerService(sellerRepository, new Idempotency(idempotencyRepository, codec));
    }
}
