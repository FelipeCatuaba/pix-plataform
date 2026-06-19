package com.transactions.pix.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transactions.pix.domain.model.PixTransaction;
import com.transactions.pix.domain.model.PixTransactionStatus;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PixTransactionRepositoryAdapterTest {

    @Mock
    private PixTransactionCrudRepository repository;

    @Mock
    private EntityManager entityManager;

    @Test
    void shouldInsertFlushAndMapTransaction() {
        // arrange
        PixTransaction transaction = transaction("tx-1");
        when(repository.save(org.mockito.ArgumentMatchers.any()))
                .thenReturn(PixTransactionPersistenceModel.fromDomain(transaction));

        // act
        PixTransaction saved = new PixTransactionRepositoryAdapter(repository, entityManager).insert(transaction);

        // assert
        assertEquals(transaction, saved);
        verify(entityManager).flush();
    }

    @Test
    void shouldMapExistingTransactionAndPreserveEmptyResult() {
        // arrange
        PixTransaction transaction = transaction("tx-2");
        when(repository.findByTransactionId("tx-2"))
                .thenReturn(Optional.of(PixTransactionPersistenceModel.fromDomain(transaction)));
        when(repository.findByTransactionId("missing")).thenReturn(Optional.empty());
        PixTransactionRepositoryAdapter adapter = new PixTransactionRepositoryAdapter(repository, entityManager);

        // act
        PixTransaction found = adapter.findByTransactionId("tx-2").orElseThrow();
        boolean missing = adapter.findByTransactionId("missing").isEmpty();

        // assert
        assertEquals(transaction, found);
        assertTrue(missing);
    }

    private PixTransaction transaction(String id) {
        Instant now = Instant.parse("2026-06-19T12:00:00Z");
        return new PixTransaction(
                1L, id, BigDecimal.TEN, "key", "invoice",
                PixTransactionStatus.PROCESSING, null, null, now, now
        );
    }
}
