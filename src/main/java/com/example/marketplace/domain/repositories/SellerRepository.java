package com.example.marketplace.domain.repositories;

import com.example.marketplace.domain.entities.Seller;
import com.example.marketplace.domain.entities.ValidatedSeller;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SellerRepository {

    Seller create(ValidatedSeller seller);

    Optional<Seller> findById(UUID id);

    List<Seller> findAll();

    Seller update(ValidatedSeller seller);

    void delete(UUID id);
}
