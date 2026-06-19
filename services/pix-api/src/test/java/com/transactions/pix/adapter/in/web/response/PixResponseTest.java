package com.transactions.pix.adapter.in.web.response;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.transactions.pix.domain.model.PixTransaction;
import com.transactions.pix.domain.model.PixTransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PixResponseTest {

    @Test
    void shouldMapTransactionToAcceptedResponse() {
        // arrange
        Instant createdAt = Instant.parse("2026-06-19T12:00:00Z");
        PixTransaction transaction = new PixTransaction(
                1L, "tx-1", BigDecimal.TEN, "key", "description",
                PixTransactionStatus.PROCESSING, null, null, createdAt, createdAt
        );

        // act
        PixResponse response = PixResponse.from(transaction);

        // assert
        assertEquals("tx-1", response.transactionId());
        assertEquals(PixTransactionStatus.PROCESSING, response.status());
        assertEquals(createdAt, response.createdAt());
    }
}
