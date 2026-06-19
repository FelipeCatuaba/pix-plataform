package com.transactions.pix.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.transactions.pix.domain.model.PixTransaction;
import com.transactions.pix.domain.model.PixTransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PixTransactionPersistenceModelTest {

    @Test
    void shouldRoundTripAllDomainFields() {
        // arrange
        Instant createdAt = Instant.parse("2026-06-19T12:00:00Z");
        Instant updatedAt = createdAt.plusSeconds(4);
        PixTransaction source = new PixTransaction(
                7L, "tx-7", new BigDecimal("45.90"), "key", "invoice",
                PixTransactionStatus.COMPLETED, "partner", null, createdAt, updatedAt
        );

        // act
        PixTransaction result = PixTransactionPersistenceModel.fromDomain(source).toDomain();

        // assert
        assertEquals(source, result);
    }
}
