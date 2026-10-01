package com.example.marketplace.application.services;

import com.example.marketplace.application.common.SellerResult;
import com.example.marketplace.domain.entities.Seller;
import com.example.marketplace.domain.entities.ValidatedSeller;

final class SellerResultMapper {

    private SellerResultMapper() {
    }

    static SellerResult fromValidatedEntity(ValidatedSeller seller) {
        return fromEntity(seller.seller());
    }

    static SellerResult fromEntity(Seller seller) {
        if (seller == null) {
            return null;
        }

        return new SellerResult(
                seller.getId(),
                seller.getName(),
                seller.getCreatedAt(),
                seller.getUpdatedAt());
    }
}
