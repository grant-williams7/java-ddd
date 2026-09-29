package com.example.marketplace.application.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.marketplace.application.common.ProductResult;
import com.example.marketplace.application.common.SellerResult;
import com.example.marketplace.domain.entities.Currency;
import com.example.marketplace.domain.entities.Money;
import com.example.marketplace.domain.entities.Product;
import com.example.marketplace.domain.entities.Seller;
import com.example.marketplace.domain.entities.ValidatedProduct;
import com.example.marketplace.domain.entities.ValidatedSeller;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResultMapperTest {

    @Test
    void newSellerResultFromEntity() {
        Instant now = Instant.now();
        Seller seller = Seller.reconstitute(UUID.randomUUID(), now, now, "Acme");

        SellerResult result = SellerResultMapper.fromEntity(seller);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(seller.getId());
        assertThat(result.name()).isEqualTo("Acme");
        assertThat(result.createdAt()).isEqualTo(now);
        assertThat(result.updatedAt()).isEqualTo(now);
    }

    @Test
    void newSellerResultFromEntity_nil() {
        assertThat(SellerResultMapper.fromEntity(null)).isNull();
    }

    @Test
    void newSellerResultFromValidatedEntity() {
        ValidatedSeller validated = ValidatedSeller.of(Seller.create("Acme"));

        SellerResult result = SellerResultMapper.fromValidatedEntity(validated);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(validated.seller().getId());
        assertThat(result.name()).isEqualTo("Acme");
    }

    @Test
    void newProductResultFromEntity() {
        Instant now = Instant.now();
        Money price = new Money(999, Currency.USD);
        UUID sellerId = UUID.randomUUID();
        Product product = Product.reconstitute(UUID.randomUUID(), now, now, "Widget", price, sellerId);

        ProductResult result = ProductResultMapper.fromEntity(product);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(product.getId());
        assertThat(result.name()).isEqualTo("Widget");
        assertThat(result.price()).isEqualTo(price);
        assertThat(result.sellerId()).isEqualTo(sellerId);
        assertThat(result.createdAt()).isEqualTo(now);
        assertThat(result.updatedAt()).isEqualTo(now);
    }

    @Test
    void newProductResultFromEntity_nil() {
        assertThat(ProductResultMapper.fromEntity(null)).isNull();
    }

    @Test
    void newProductResultFromValidatedEntity() {
        ValidatedSeller validatedSeller = ValidatedSeller.of(Seller.create("Acme"));
        Money price = new Money(999, Currency.USD);
        ValidatedProduct validatedProduct = ValidatedProduct.of(Product.create("Widget", price, validatedSeller));

        ProductResult result = ProductResultMapper.fromValidatedEntity(validatedProduct);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(validatedProduct.product().getId());
        assertThat(result.name()).isEqualTo("Widget");
        assertThat(result.price()).isEqualTo(price);
        assertThat(result.sellerId()).isEqualTo(validatedSeller.seller().getId());
    }
}
