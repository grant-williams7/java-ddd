package com.example.marketplace.infrastructure.db.postgres;

import com.example.marketplace.domain.entities.Currency;
import com.example.marketplace.domain.entities.Money;
import com.example.marketplace.domain.entities.Product;
import com.example.marketplace.domain.entities.ProductNotFoundException;
import com.example.marketplace.domain.entities.ValidatedProduct;
import com.example.marketplace.domain.repositories.ProductRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
class JdbcProductRepository implements ProductRepository {

    private static final String CREATE_PRODUCT = """
            INSERT INTO products (id, name, price_minor_units, currency, seller_id, created_at, updated_at)
            VALUES (:id, :name, :price_minor_units, :currency, :seller_id, :created_at, :updated_at)
            RETURNING *""";

    private static final String GET_PRODUCT_BY_ID = """
            SELECT p.id, p.name, p.price_minor_units, p.currency, p.seller_id, p.created_at, p.updated_at
            FROM products p
            JOIN sellers s ON p.seller_id = s.id
            WHERE p.id = :id AND p.deleted_at IS NULL AND s.deleted_at IS NULL""";

    private static final String GET_ALL_PRODUCTS = """
            SELECT p.id, p.name, p.price_minor_units, p.currency, p.seller_id, p.created_at, p.updated_at
            FROM products p
            JOIN sellers s ON p.seller_id = s.id
            WHERE p.deleted_at IS NULL AND s.deleted_at IS NULL
            ORDER BY p.created_at DESC""";

    private static final String UPDATE_PRODUCT = """
            UPDATE products
            SET name = :name, price_minor_units = :price_minor_units, currency = :currency, seller_id = :seller_id, updated_at = :updated_at
            WHERE id = :id AND deleted_at IS NULL""";

    private static final String DELETE_PRODUCT = """
            UPDATE products SET deleted_at = NOW() WHERE id = :id""";

    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;
    private final OutboxWriter outbox;

    JdbcProductRepository(JdbcClient jdbc, TransactionTemplate transactions, OutboxWriter outbox) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.outbox = outbox;
    }

    /**
     * Stores the product and its recorded domain events in one transaction
     * (transactional outbox): both commit or neither does. The read-back runs
     * inside the same transaction, so a failure there can't surface after the
     * commit already happened.
     */
    @Override
    public Product create(ValidatedProduct validated) {
        Product product = validated.product();

        return Objects.requireNonNull(transactions.execute(status -> {
            jdbc.sql(CREATE_PRODUCT)
                    .param("id", product.getId())
                    .param("name", product.getName())
                    .param("price_minor_units", product.getPrice().minorUnits())
                    .param("currency", product.getPrice().currency().code())
                    .param("seller_id", product.getSellerId())
                    .param("created_at", JdbcTimestamps.toDatabase(product.getCreatedAt()))
                    .param("updated_at", JdbcTimestamps.toDatabase(product.getUpdatedAt()))
                    .query()
                    .singleRow();

            outbox.insert(product.pullEvents());

            return findById(product.getId()).orElseThrow(() ->
                    new IllegalStateException("product " + product.getId() + " not readable after insert"));
        }));
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return jdbc.sql(GET_PRODUCT_BY_ID)
                .param("id", id)
                .query(JdbcProductRepository::productFromRow)
                .optional();
    }

    @Override
    public List<Product> findAll() {
        return jdbc.sql(GET_ALL_PRODUCTS)
                .query(JdbcProductRepository::productFromRow)
                .list();
    }

    @Override
    public Product update(ValidatedProduct validated) {
        Product product = validated.product();

        int rows = jdbc.sql(UPDATE_PRODUCT)
                .param("id", product.getId())
                .param("name", product.getName())
                .param("price_minor_units", product.getPrice().minorUnits())
                .param("currency", product.getPrice().currency().code())
                .param("seller_id", product.getSellerId())
                .param("updated_at", JdbcTimestamps.toDatabase(product.getUpdatedAt()))
                .update();
        if (rows == 0) {
            // Nothing matched: the product doesn't exist or is soft-deleted.
            throw new ProductNotFoundException();
        }

        return findById(product.getId()).orElseThrow(ProductNotFoundException::new);
    }

    @Override
    public void delete(UUID id) {
        jdbc.sql(DELETE_PRODUCT).param("id", id).update();
    }

    /** Goes through the {@code Money} constructor, so an unsupported stored currency is an error. */
    private static Product productFromRow(ResultSet row, int rowNumber) throws SQLException {
        Money price = new Money(row.getLong("price_minor_units"), new Currency(row.getString("currency")));

        return Product.reconstitute(
                row.getObject("id", UUID.class),
                JdbcTimestamps.fromDatabase(row.getObject("created_at", OffsetDateTime.class)),
                JdbcTimestamps.fromDatabase(row.getObject("updated_at", OffsetDateTime.class)),
                row.getString("name"),
                price,
                row.getObject("seller_id", UUID.class));
    }
}
