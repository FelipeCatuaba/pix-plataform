package com.transactions.pix.adapter.out.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.transactions.pix.domain.model.PixTransaction;
import com.transactions.pix.domain.model.PixTransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CachedPixStatusTest {

    @Test
    void shouldRoundTripTransactionStatusFields() {
        // arrange
        Instant createdAt = Instant.parse("2026-06-19T12:00:00Z");
        Instant updatedAt = createdAt.plusSeconds(10);
        PixTransaction source = new PixTransaction(
                1L, "tx-1", BigDecimal.TEN, "key", "invoice",
                PixTransactionStatus.COMPLETED, "partner", null, createdAt, updatedAt
        );

        // act
        PixTransaction restored = CachedPixStatus.from(source).toTransaction();

        // assert
        assertEquals("tx-1", restored.transactionId());
        assertEquals(PixTransactionStatus.COMPLETED, restored.status());
        assertEquals(createdAt, restored.createdAt());
        assertEquals(updatedAt, restored.updatedAt());
        assertNull(restored.amount());
    }
}
