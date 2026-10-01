package com.example.marketplace.infrastructure.db.postgres;

import static com.example.marketplace.infrastructure.db.postgres.RepositoryTestSupport.createTestSeller;
import static com.example.marketplace.infrastructure.db.postgres.RepositoryTestSupport.productRepository;
import static com.example.marketplace.infrastructure.db.postgres.RepositoryTestSupport.sellerRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.marketplace.domain.entities.Currency;
import com.example.marketplace.domain.entities.Money;
import com.example.marketplace.domain.entities.Product;
import com.example.marketplace.domain.entities.ProductNotFoundException;
import com.example.marketplace.domain.entities.Timestamps;
import com.example.marketplace.domain.entities.ValidatedProduct;
import com.example.marketplace.domain.entities.ValidatedSeller;
import com.example.marketplace.testhelpers.PostgresTestContainer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class JdbcProductRepositoryIT {

    private static PostgresTestContainer database;
    private JdbcProductRepository repository;

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
        repository = productRepository(database);
    }

    @Test
    void create() {
        ValidatedSeller seller = createTestSeller(database, "Test Seller");
        ValidatedProduct product = ValidatedProduct.of(
                Product.create("Test Product", new Money(9999, Currency.USD), seller));

        Product created = repository.create(product);

        assertThat(created).isNotNull();
        assertThat(created.getName()).isEqualTo(product.product().getName());
        assertThat(created.getPrice()).isEqualTo(product.product().getPrice());
        assertThat(created.getPrice().minorUnits()).isEqualTo(9999);
        assertThat(created.getPrice().currency()).isEqualTo(Currency.USD);
        assertThat(created.getSellerId()).isEqualTo(seller.seller().getId());
        assertThat(created.getId()).isNotNull();
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(created.getUpdatedAt()).isNotNull();
    }

    @Test
    void findById() {
        ValidatedSeller seller = createTestSeller(database, "Test Seller");
        Product created = repository.create(ValidatedProduct.of(
                Product.create("Test Product", new Money(9999, Currency.EUR), seller)));

        assertThat(repository.findById(created.getId())).hasValueSatisfying(found -> {
            assertThat(found.getId()).isEqualTo(created.getId());
            assertThat(found.getName()).isEqualTo(created.getName());
            assertThat(found.getPrice()).isEqualTo(created.getPrice());
            assertThat(found.getSellerId()).isEqualTo(seller.seller().getId());
        });
    }

    @Test
    void findById_notFound() {
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findAll() {
        ValidatedSeller seller = createTestSeller(database, "Test Seller");
        Product first = repository.create(ValidatedProduct.of(
                Product.create("Product 1", new Money(1000, Currency.USD), seller)));
        Product second = repository.create(ValidatedProduct.of(
                Product.create("Product 2", new Money(2000, Currency.EUR), seller)));

        List<Product> products = repository.findAll();

        assertThat(products).hasSize(2);
        assertThat(products).filteredOn(p -> p.getId().equals(first.getId())).singleElement().satisfies(p -> {
            assertThat(p.getName()).isEqualTo("Product 1");
            assertThat(p.getPrice()).isEqualTo(new Money(1000, Currency.USD));
        });
        assertThat(products).filteredOn(p -> p.getId().equals(second.getId())).singleElement().satisfies(p -> {
            assertThat(p.getName()).isEqualTo("Product 2");
            assertThat(p.getPrice()).isEqualTo(new Money(2000, Currency.EUR));
        });
    }

    @Test
    void findAll_empty() {
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void update() {
        ValidatedSeller seller = createTestSeller(database, "Test Seller");
        Product created = repository.create(ValidatedProduct.of(
                Product.create("Original Product", new Money(5000, Currency.USD), seller)));

        Product updated = Product.reconstitute(created.getId(), created.getCreatedAt(), Timestamps.now(),
                "Updated Product", new Money(7500, Currency.USD), created.getSellerId());

        Product result = repository.update(ValidatedProduct.of(updated));

        assertThat(result.getName()).isEqualTo("Updated Product");
        assertThat(result.getPrice()).isEqualTo(new Money(7500, Currency.USD));
        assertThat(result.getId()).isEqualTo(created.getId());
        assertThat(result.getUpdatedAt()).isAfter(created.getUpdatedAt());
    }

    @Test
    void update_notFound() {
        Product missing = Product.reconstitute(UUID.randomUUID(), Timestamps.now(), Timestamps.now(),
                "Non-existent Product", new Money(10000, Currency.USD), UUID.randomUUID());

        assertThatThrownBy(() -> repository.update(ValidatedProduct.of(missing)))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void delete() {
        ValidatedSeller seller = createTestSeller(database, "Test Seller");
        Product created = repository.create(ValidatedProduct.of(
                Product.create("Test Product", new Money(9999, Currency.USD), seller)));

        repository.delete(created.getId());

        assertThat(repository.findById(created.getId())).isEmpty();
    }

    /** A soft delete of a missing row changes nothing and isn't an error. */
    @Test
    void delete_notFound() {
        assertThatCode(() -> repository.delete(UUID.randomUUID())).doesNotThrowAnyException();
    }

    @Test
    void create_withInvalidSeller() {
        Product product = Product.reconstitute(UUID.randomUUID(), Timestamps.now(), Timestamps.now(),
                "Test Product", new Money(9999, Currency.USD), UUID.randomUUID());

        // No such seller: the foreign key rejects the insert.
        assertThatThrownBy(() -> repository.create(ValidatedProduct.of(product)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * The product insert and the outbox insert share one transaction. Here both
     * inserts succeed, then the read-back fails (the seller is soft-deleted, so
     * the join hides the new row): nothing may survive, outbox row included.
     */
    @Test
    void create_failureRollsBackOutboxRows() {
        ValidatedSeller seller = createTestSeller(database, "Soon Deleted");
        sellerRepository(database).delete(seller.seller().getId());
        ValidatedProduct product = ValidatedProduct.of(
                Product.create("Orphan", new Money(100, Currency.USD), seller));

        assertThatThrownBy(() -> repository.create(product)).isInstanceOf(IllegalStateException.class);

        assertThat(database.count("products")).isZero();
        assertThat(database.count("outbox_events")).isZero();
    }
}
