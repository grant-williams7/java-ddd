package com.example.marketplace.domain.entities;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SellerTest {

    @Test
    void newSeller() {
        Seller seller = Seller.create("Example Seller");

        assertThat(seller.getName()).isEqualTo("Example Seller");
        assertThat(seller.getId()).isNotNull().isNotEqualTo(Uuids.NIL);
    }
}
