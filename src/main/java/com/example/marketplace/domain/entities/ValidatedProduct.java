package com.example.marketplace.domain.entities;

/**
 * A product that passed validation. Repository write methods accept only this
 * type, so the compiler enforces that nothing unvalidated gets persisted.
 */
public final class ValidatedProduct {

    private final Product product;

    private ValidatedProduct(Product product) {
        this.product = product;
    }

    /** Validates a copy, so later changes to the argument can't leak in. */
    public static ValidatedProduct of(Product product) {
        Product copy = product.copy();
        copy.validate();
        return new ValidatedProduct(copy);
    }

    public Product product() {
        return product;
    }

    public boolean isValid() {
        return product != null;
    }
}
