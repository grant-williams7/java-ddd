package com.example.marketplace.domain.entities;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.marketplace.domain.events.DomainEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductTest {

    @Test
    void newProduct() {
        ValidatedSeller validatedSeller = ValidatedSeller.of(Seller.create("Example Seller"));
        Money price = new Money(1000, Currency.USD);

        Product product = Product.create("Example Product", price, validatedSeller);

        assertThat(product.getName()).isEqualTo("Example Product");
        assertThat(product.getPrice()).isEqualTo(price);
        assertThat(product.getSellerId()).isEqualTo(validatedSeller.seller().getId());
        assertThat(product.getId()).isNotNull().isNotEqualTo(Uuids.NIL);
    }

    @Test
    void pullEvents_clearsEvents() {
        Product product = Product.create("Widget", new Money(999, Currency.USD),
                ValidatedSeller.of(Seller.create("Acme")));

        List<DomainEvent> first = product.pullEvents();
        List<DomainEvent> second = product.pullEvents();

        assertThat(first).hasSize(1);
        assertThat(second).isEmpty();
    }
}
