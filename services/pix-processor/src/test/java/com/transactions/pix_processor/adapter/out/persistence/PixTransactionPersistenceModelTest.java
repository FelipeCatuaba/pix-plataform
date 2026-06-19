package com.transactions.pix_processor.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertNull;

import com.transactions.pix_processor.domain.model.PixTransaction;
import org.junit.jupiter.api.Test;

class PixTransactionPersistenceModelTest {

    @Test
    void shouldMapNewPersistenceModelDefaultState() {
        // arrange
        PixTransactionPersistenceModel model = new PixTransactionPersistenceModel();

        // act
        PixTransaction transaction = model.toDomain();

        // assert
        assertNull(transaction.id());
        assertNull(transaction.transactionId());
        assertNull(transaction.status());
        assertNull(transaction.createdAt());
    }
}
