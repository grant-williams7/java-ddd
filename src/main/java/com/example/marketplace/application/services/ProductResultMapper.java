package com.example.marketplace.application.services;

import com.example.marketplace.application.common.ProductResult;
import com.example.marketplace.domain.entities.Product;
import com.example.marketplace.domain.entities.ValidatedProduct;

final class ProductResultMapper {

    private ProductResultMapper() {
    }

    static ProductResult fromValidatedEntity(ValidatedProduct product) {
        return fromEntity(product.product());
    }

    static ProductResult fromEntity(Product product) {
        if (product == null) {
            return null;
        }

        return new ProductResult(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getSellerId(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
