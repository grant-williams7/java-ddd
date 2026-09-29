package com.example.marketplace.domain.repositories;

import com.example.marketplace.domain.entities.Product;
import com.example.marketplace.domain.entities.ValidatedProduct;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {

    Product create(ValidatedProduct product);

    Optional<Product> findById(UUID id);

    List<Product> findAll();

    Product update(ValidatedProduct product);

    void delete(UUID id);
}
