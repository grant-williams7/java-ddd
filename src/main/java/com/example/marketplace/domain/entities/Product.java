package com.example.marketplace.domain.entities;

import com.example.marketplace.domain.events.DomainEvent;
import com.example.marketplace.domain.events.ProductCreated;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Product {

    private final UUID id;
    private final Instant createdAt;
    private Instant updatedAt;
    private String name;
    private Money price;
    private UUID sellerId;

    private final List<DomainEvent> domainEvents;

    private Product(UUID id, Instant createdAt, Instant updatedAt, String name, Money price, UUID sellerId,
            List<DomainEvent> domainEvents) {
        this.id = id;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.name = name;
        this.price = price;
        this.sellerId = sellerId;
        this.domainEvents = new ArrayList<>(domainEvents);
    }

    /**
     * Requires a {@link ValidatedSeller}, so a product can only ever be created
     * against a seller that passed validation. The product keeps just the
     * seller's id: sellers are a separate aggregate and must not be embedded.
     */
    public static Product create(String name, Money price, ValidatedSeller seller) {
        Instant now = Timestamps.now();
        UUID sellerId = seller.seller().getId();
        Product product = new Product(Uuids.newV7(), now, now, name, price, sellerId, List.of());

        product.recordEvent(ProductCreated.of(
                product.id, name, price.minorUnits(), price.currency().code(), sellerId));

        return product;
    }

    /**
     * Rebuilds a product from stored state without validating or recording
     * events. Only repositories should call this.
     */
    public static Product reconstitute(UUID id, Instant createdAt, Instant updatedAt, String name, Money price,
            UUID sellerId) {
        return new Product(id, createdAt, updatedAt, name, price, sellerId, List.of());
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getName() {
        return name;
    }

    public Money getPrice() {
        return price;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public void updateName(String name) {
        this.name = name;
        this.updatedAt = Timestamps.now();

        validate();
    }

    public void updatePrice(Money price) {
        this.price = price;
        this.updatedAt = Timestamps.now();

        validate();
    }

    /** Moves the product to a different, validated seller. */
    public void assignSeller(ValidatedSeller seller) {
        this.sellerId = seller.seller().getId();
        this.updatedAt = Timestamps.now();

        validate();
    }

    /**
     * Returns the recorded domain events and clears them. The repository stores
     * them in the same transaction as the product (transactional outbox), so
     * callers pull exactly once per save.
     */
    public List<DomainEvent> pullEvents() {
        List<DomainEvent> pulled = List.copyOf(domainEvents);
        domainEvents.clear();
        return pulled;
    }

    void validate() {
        if (name == null || name.isEmpty()) {
            throw new ValidationException("name must not be empty");
        }
        if (price == null || price.minorUnits() == 0) {
            throw new ValidationException("price must be greater than 0");
        }
        if (sellerId == null || sellerId.equals(Uuids.NIL)) {
            throw new ValidationException("seller id must not be empty");
        }
        if (Timestamps.orZero(createdAt).isAfter(Timestamps.orZero(updatedAt))) {
            throw new ValidationException("created_at must be before updated_at");
        }
    }

    Product copy() {
        return new Product(id, createdAt, updatedAt, name, price, sellerId, domainEvents);
    }

    private void recordEvent(DomainEvent event) {
        domainEvents.add(event);
    }
}
