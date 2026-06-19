package com.transactions.pix.adapter.in.web.request;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PixRequestTest {

    @Test
    void shouldExposeRequestValues() {
        // arrange
        BigDecimal amount = new BigDecimal("125.40");

        // act
        PixRequest request = new PixRequest("tx-1", amount, "key@example.com", "Invoice");

        // assert
        assertEquals("tx-1", request.transactionId());
        assertEquals(amount, request.amount());
        assertEquals("key@example.com", request.pixKey());
        assertEquals("Invoice", request.description());
    }
}
