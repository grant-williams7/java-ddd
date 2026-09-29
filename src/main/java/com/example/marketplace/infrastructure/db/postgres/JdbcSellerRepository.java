package com.example.marketplace.infrastructure.db.postgres;

import com.example.marketplace.domain.entities.Seller;
import com.example.marketplace.domain.entities.SellerNotFoundException;
import com.example.marketplace.domain.entities.ValidatedSeller;
import com.example.marketplace.domain.repositories.SellerRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSellerRepository implements SellerRepository {

    private static final String CREATE_SELLER = """
            INSERT INTO sellers (id, name, created_at, updated_at)
            VALUES (:id, :name, :created_at, :updated_at)
            RETURNING *""";

    private static final String GET_SELLER_BY_ID = """
            SELECT id, name, created_at, updated_at
            FROM sellers
            WHERE id = :id AND deleted_at IS NULL""";

    private static final String GET_ALL_SELLERS = """
            SELECT id, name, created_at, updated_at
            FROM sellers
            WHERE deleted_at IS NULL
            ORDER BY created_at DESC""";

    private static final String UPDATE_SELLER = """
            UPDATE sellers
            SET name = :name, updated_at = :updated_at
            WHERE id = :id AND deleted_at IS NULL""";

    private static final String DELETE_SELLER = """
            UPDATE sellers SET deleted_at = NOW() WHERE id = :id""";

    private final JdbcClient jdbc;

    JdbcSellerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Seller create(ValidatedSeller validated) {
        Seller seller = validated.seller();

        jdbc.sql(CREATE_SELLER)
                .param("id", seller.getId())
                .param("name", seller.getName())
                .param("created_at", JdbcTimestamps.toDatabase(seller.getCreatedAt()))
                .param("updated_at", JdbcTimestamps.toDatabase(seller.getUpdatedAt()))
                .query()
                .singleRow();

        return findById(seller.getId()).orElseThrow(SellerNotFoundException::new);
    }

    @Override
    public Optional<Seller> findById(UUID id) {
        return jdbc.sql(GET_SELLER_BY_ID)
                .param("id", id)
                .query(JdbcSellerRepository::sellerFromRow)
                .optional();
    }

    @Override
    public List<Seller> findAll() {
        return jdbc.sql(GET_ALL_SELLERS)
                .query(JdbcSellerRepository::sellerFromRow)
                .list();
    }

    @Override
    public Seller update(ValidatedSeller validated) {
        Seller seller = validated.seller();

        int rows = jdbc.sql(UPDATE_SELLER)
                .param("id", seller.getId())
                .param("name", seller.getName())
                .param("updated_at", JdbcTimestamps.toDatabase(seller.getUpdatedAt()))
                .update();
        if (rows == 0) {
            // Nothing matched: the seller doesn't exist or is soft-deleted.
            throw new SellerNotFoundException();
        }

        return findById(seller.getId()).orElseThrow(SellerNotFoundException::new);
    }

    @Override
    public void delete(UUID id) {
        jdbc.sql(DELETE_SELLER).param("id", id).update();
    }

    private static Seller sellerFromRow(ResultSet row, int rowNumber) throws SQLException {
        return Seller.reconstitute(
                row.getObject("id", UUID.class),
                JdbcTimestamps.fromDatabase(row.getObject("created_at", OffsetDateTime.class)),
                JdbcTimestamps.fromDatabase(row.getObject("updated_at", OffsetDateTime.class)),
                row.getString("name"));
    }
}
