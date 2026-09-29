package com.example.marketplace.infrastructure.db.postgres;

import com.example.marketplace.domain.entities.Seller;
import com.example.marketplace.domain.entities.ValidatedSeller;
import com.example.marketplace.testhelpers.PostgresTestContainer;
import tools.jackson.databind.json.JsonMapper;

final class RepositoryTestSupport {

    private RepositoryTestSupport() {
    }

    static JdbcProductRepository productRepository(PostgresTestContainer database) {
        OutboxWriter outbox = new OutboxWriter(database.jdbcClient(),
                new OutboxPayloads(JsonMapper.builder().build()));
        return new JdbcProductRepository(database.jdbcClient(), database.transactionTemplate(), outbox);
    }

    static JdbcSellerRepository sellerRepository(PostgresTestContainer database) {
        return new JdbcSellerRepository(database.jdbcClient());
    }

    static JdbcIdempotencyRepository idempotencyRepository(PostgresTestContainer database) {
        return new JdbcIdempotencyRepository(database.jdbcClient());
    }

    static ValidatedSeller createTestSeller(PostgresTestContainer database, String name) {
        ValidatedSeller seller = ValidatedSeller.of(Seller.create(name));
        sellerRepository(database).create(seller);
        return seller;
    }
}
