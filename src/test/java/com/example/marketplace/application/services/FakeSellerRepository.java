package com.example.marketplace.application.services;

import com.example.marketplace.domain.entities.Seller;
import com.example.marketplace.domain.entities.ValidatedSeller;
import com.example.marketplace.domain.repositories.SellerRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

class FakeSellerRepository implements SellerRepository {

    final List<ValidatedSeller> sellers = new ArrayList<>();

    @Override
    public Seller create(ValidatedSeller seller) {
        sellers.add(seller);
        return seller.seller();
    }

    @Override
    public Optional<Seller> findById(UUID id) {
        return sellers.stream()
                .map(ValidatedSeller::seller)
                .filter(seller -> seller.getId().equals(id))
                .findFirst();
    }

    @Override
    public List<Seller> findAll() {
        return sellers.stream().map(ValidatedSeller::seller).toList();
    }

    @Override
    public Seller update(ValidatedSeller seller) {
        for (int i = 0; i < sellers.size(); i++) {
            if (sellers.get(i).seller().getId().equals(seller.seller().getId())) {
                sellers.set(i, seller);
                return seller.seller();
            }
        }
        throw new IllegalStateException("seller not found for update");
    }

    @Override
    public void delete(UUID id) {
        if (!sellers.removeIf(seller -> seller.seller().getId().equals(id))) {
            throw new IllegalStateException("seller not found for deletion");
        }
    }
}
