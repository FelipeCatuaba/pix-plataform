package com.transactions.pix.adapter.in.web.response;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.transactions.pix.domain.model.PixTransaction;
import com.transactions.pix.domain.model.PixTransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PixStatusResponseTest {

    @Test
    void shouldMapTransactionToStatusResponse() {
        // arrange
        Instant createdAt = Instant.parse("2026-06-19T12:00:00Z");
        Instant updatedAt = createdAt.plusSeconds(5);
        PixTransaction transaction = new PixTransaction(
                1L, "tx-2", BigDecimal.TEN, "key", "description",
                PixTransactionStatus.COMPLETED, "partner-1", null, createdAt, updatedAt
        );

        // act
        PixStatusResponse response = PixStatusResponse.from(transaction);

        // assert
        assertEquals("tx-2", response.transactionId());
        assertEquals(PixTransactionStatus.COMPLETED, response.status());
        assertEquals(createdAt, response.createdAt());
        assertEquals(updatedAt, response.updatedAt());
    }
}
