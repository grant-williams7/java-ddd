package com.example.marketplace.domain.entities;

public final class SellerNotFoundException extends DomainException {

    public SellerNotFoundException() {
        super("seller not found");
    }
}
