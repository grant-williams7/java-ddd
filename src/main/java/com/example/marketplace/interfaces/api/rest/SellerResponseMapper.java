package com.example.marketplace.interfaces.api.rest;

import com.example.marketplace.application.common.SellerResult;
import java.util.List;

final class SellerResponseMapper {

    private SellerResponseMapper() {
    }

    static SellerResponse toSellerResponse(SellerResult seller) {
        return new SellerResponse(
                seller.id().toString(),
                seller.name(),
                seller.createdAt(),
                seller.updatedAt());
    }

    /** An empty result is {@code []}, never {@code null}: the spec types {@code sellers} as an array. */
    static ListSellersResponse toSellerListResponse(List<SellerResult> sellers) {
        List<SellerResult> results = sellers == null ? List.of() : sellers;
        return new ListSellersResponse(results.stream().map(SellerResponseMapper::toSellerResponse).toList());
    }
}
