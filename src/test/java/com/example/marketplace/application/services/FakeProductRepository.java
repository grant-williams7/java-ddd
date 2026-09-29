package com.example.marketplace.application.services;

import com.example.marketplace.domain.entities.Product;
import com.example.marketplace.domain.entities.ValidatedProduct;
import com.example.marketplace.domain.repositories.ProductRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class FakeProductRepository implements ProductRepository {

    final List<ValidatedProduct> products = new ArrayList<>();

    @Override
    public Product create(ValidatedProduct product) {
        products.add(product);
        return product.product();
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return products.stream()
                .map(ValidatedProduct::product)
                .filter(product -> product.getId().equals(id))
                .findFirst();
    }

    @Override
    public List<Product> findAll() {
        return products.stream().map(ValidatedProduct::product).toList();
    }

    @Override
    public Product update(ValidatedProduct product) {
        for (int i = 0; i < products.size(); i++) {
            if (products.get(i).product().getId().equals(product.product().getId())) {
                products.set(i, product);
                return product.product();
            }
        }
        throw new IllegalStateException("product not found for update");
    }

    @Override
    public void delete(UUID id) {
        if (!products.removeIf(product -> product.product().getId().equals(id))) {
            throw new IllegalStateException("product not found for delete");
        }
    }
}
