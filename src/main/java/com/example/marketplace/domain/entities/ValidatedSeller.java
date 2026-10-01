package com.example.marketplace.domain.entities;

/**
 * A seller that passed validation. Repository write methods accept only this
 * type, so the compiler enforces that nothing unvalidated gets persisted.
 */
public final class ValidatedSeller {

    private final Seller seller;

    private ValidatedSeller(Seller seller) {
        this.seller = seller;
    }

    /** Validates a copy, so later changes to the argument can't leak in. */
    public static ValidatedSeller of(Seller seller) {
        Seller copy = seller.copy();
        copy.validate();
        return new ValidatedSeller(copy);
    }

    public Seller seller() {
        return seller;
    }

    public boolean isValid() {
        return seller != null;
    }
}
