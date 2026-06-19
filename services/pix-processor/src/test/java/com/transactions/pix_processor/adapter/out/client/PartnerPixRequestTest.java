package com.transactions.pix_processor.adapter.out.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PartnerPixRequestTest {

    @Test
    void shouldExposePartnerRequestValues() {
        // arrange
        BigDecimal amount = new BigDecimal("99.90");

        // act
        PartnerPixRequest request = new PartnerPixRequest("tx-1", amount, "key", "invoice");

        // assert
        assertEquals("tx-1", request.transactionId());
        assertEquals(amount, request.amount());
        assertEquals("key", request.pixKey());
        assertEquals("invoice", request.description());
    }
}
