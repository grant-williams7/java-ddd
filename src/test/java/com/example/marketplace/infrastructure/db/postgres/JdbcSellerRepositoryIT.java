package com.example.marketplace.infrastructure.db.postgres;

import static com.example.marketplace.infrastructure.db.postgres.RepositoryTestSupport.productRepository;
import static com.example.marketplace.infrastructure.db.postgres.RepositoryTestSupport.sellerRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.marketplace.domain.entities.Currency;
import com.example.marketplace.domain.entities.Money;
import com.example.marketplace.domain.entities.Product;
import com.example.marketplace.domain.entities.Seller;
import com.example.marketplace.domain.entities.SellerNotFoundException;
import com.example.marketplace.domain.entities.Timestamps;
import com.example.marketplace.domain.entities.ValidatedProduct;
import com.example.marketplace.domain.entities.ValidatedSeller;
import com.example.marketplace.domain.entities.ValidationException;
import com.example.marketplace.testhelpers.PostgresTestContainer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcSellerRepositoryIT {

    private static PostgresTestContainer database;
    private JdbcSellerRepository repository;

    @BeforeAll
    static void startDatabase() {
        database = PostgresTestContainer.start();
    }

    @AfterAll
    static void stopDatabase() {
        database.close();
    }

    @BeforeEach
    void setUp() {
        database.truncateTables();
        repository = sellerRepository(database);
    }

    @Test
    void create() {
        ValidatedSeller seller = ValidatedSeller.of(Seller.create("Test Seller"));

        Seller created = repository.create(seller);

        assertThat(created.getName()).isEqualTo(seller.seller().getName());
        assertThat(created.getId()).isNotNull();
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getUpdatedAt()).isNotNull();
    }

    @Test
    void findById() {
        Seller created = repository.create(ValidatedSeller.of(Seller.create("Test Seller")));

        assertThat(repository.findById(created.getId())).hasValueSatisfying(found -> {
            assertThat(found.getId()).isEqualTo(created.getId());
            assertThat(found.getName()).isEqualTo(created.getName());
            assertThat(found.getCreatedAt()).isEqualTo(created.getCreatedAt());
            assertThat(found.getUpdatedAt()).isEqualTo(created.getUpdatedAt());
        });
    }

    @Test
    void findById_notFound() {
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findAll() {
        Seller first = repository.create(ValidatedSeller.of(Seller.create("Seller One")));
        Seller second = repository.create(ValidatedSeller.of(Seller.create("Seller Two")));

        List<Seller> sellers = repository.findAll();

        assertThat(sellers).hasSize(2);
        assertThat(sellers).filteredOn(s -> s.getId().equals(first.getId()))
                .singleElement().extracting(Seller::getName).isEqualTo("Seller One");
        assertThat(sellers).filteredOn(s -> s.getId().equals(second.getId()))
                .singleElement().extracting(Seller::getName).isEqualTo("Seller Two");
    }

    @Test
    void findAll_empty() {
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void update() {
        Seller created = repository.create(ValidatedSeller.of(Seller.create("Original Seller")));
        Seller updated = Seller.reconstitute(created.getId(), created.getCreatedAt(), Timestamps.now(),
                "Updated Seller");

        Seller result = repository.update(ValidatedSeller.of(updated));

        assertThat(result.getName()).isEqualTo("Updated Seller");
        assertThat(result.getId()).isEqualTo(created.getId());
        assertThat(result.getUpdatedAt()).isAfter(created.getUpdatedAt());
    }

    @Test
    void update_notFound() {
        Seller missing = Seller.reconstitute(UUID.randomUUID(), Timestamps.now(), Timestamps.now(),
                "Non-existent Seller");

        assertThatThrownBy(() -> repository.update(ValidatedSeller.of(missing)))
                .isInstanceOf(SellerNotFoundException.class);
    }

    @Test
    void delete() {
        Seller created = repository.create(ValidatedSeller.of(Seller.create("Test Seller")));

        repository.delete(created.getId());

        assertThat(repository.findById(created.getId())).isEmpty();
    }

    /** A soft delete of a missing row changes nothing and isn't an error. */
    @Test
    void delete_notFound() {
        assertThatCode(() -> repository.delete(UUID.randomUUID())).doesNotThrowAnyException();
    }

    @Test
    void delete_withExistingProducts() {
        ValidatedSeller seller = ValidatedSeller.of(Seller.create("Test Seller"));
        Seller created = repository.create(seller);
        productRepository(database).create(ValidatedProduct.of(
                Product.create("Test Product", new Money(9999, Currency.USD), seller)));

        // A soft delete succeeds even though products still reference the seller.
        assertThatCode(() -> repository.delete(created.getId())).doesNotThrowAnyException();

        assertThat(repository.findById(created.getId())).isEmpty();
    }

    @Test
    void create_emptyName() {
        Seller seller = Seller.reconstitute(UUID.randomUUID(), Timestamps.now(), Timestamps.now(), "");

        // Validation rejects it before it could reach the repository.
        assertThatThrownBy(() -> ValidatedSeller.of(seller)).isInstanceOf(ValidationException.class);
    }

    @Test
    void create_longName() {
        String longName = "A".repeat(1000);

        Seller created = repository.create(ValidatedSeller.of(Seller.create(longName)));

        assertThat(created.getName()).isEqualTo(longName);
    }
}
