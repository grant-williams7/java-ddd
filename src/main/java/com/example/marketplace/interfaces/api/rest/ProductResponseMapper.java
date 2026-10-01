package com.example.marketplace.interfaces.api.rest;

import com.example.marketplace.application.common.ProductResult;
import java.util.List;

final class ProductResponseMapper {

    private ProductResponseMapper() {
    }

    static ProductResponse toProductResponse(ProductResult product) {
        return new ProductResponse(
                product.id().toString(),
                product.name(),
                product.price().minorUnits(),
                product.price().currency().code(),
                product.sellerId().toString(),
                product.createdAt(),
                product.updatedAt());
    }

    static ListProductsResponse toProductListResponse(List<ProductResult> products) {
        List<ProductResult> results = products == null ? List.of() : products;
        return new ListProductsResponse(results.stream().map(ProductResponseMapper::toProductResponse).toList());
    }
}
