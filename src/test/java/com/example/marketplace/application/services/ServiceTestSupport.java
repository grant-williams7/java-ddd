package com.example.marketplace.application.services;

import com.example.marketplace.application.command.CreateProductCommand;
import com.example.marketplace.application.command.CreateSellerCommand;
import com.example.marketplace.domain.entities.Currency;
import com.example.marketplace.domain.entities.Seller;
import com.example.marketplace.domain.entities.ValidatedSeller;
import java.util.UUID;

final class ServiceTestSupport {

    private ServiceTestSupport() {
    }

    static DefaultProductService productService(FakeProductRepository products, FakeSellerRepository sellers) {
        return new DefaultProductService(products, sellers,
                new Idempotency(new FakeIdempotencyRepository(), new TestJsonCodec()));
    }

    static DefaultSellerService sellerService(FakeSellerRepository sellers) {
        return new DefaultSellerService(sellers,
                new Idempotency(new FakeIdempotencyRepository(), new TestJsonCodec()));
    }

    static CreateProductCommand createProductCommand(String name, long priceMinorUnits, UUID sellerId) {
        return new CreateProductCommand("", null, name, priceMinorUnits, Currency.USD, sellerId);
    }

    static CreateSellerCommand createSellerCommand(String name) {
        return new CreateSellerCommand("", name);
    }

    static ValidatedSeller persistedSeller(FakeSellerRepository sellers) {
        ValidatedSeller seller = ValidatedSeller.of(Seller.create("John Doe"));
        sellers.create(seller);
        return seller;
    }
}
