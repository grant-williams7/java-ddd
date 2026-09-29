package com.example.marketplace.domain.entities;

public final class ProductNotFoundException extends DomainException {

    public ProductNotFoundException() {
        super("product not found");
    }
}
