package com.transactions.pix_processor.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void shouldMapFindAndDelegateStatusUpdates() {
        // arrange
        when(repository.findByTransactionId("missing")).thenReturn(Optional.empty());
        PixTransactionRepositoryAdapter adapter = new PixTransactionRepositoryAdapter(repository);
        Instant updatedAt = Instant.parse("2026-06-19T12:00:00Z");

        // act
        boolean missing = adapter.findByTransactionId("missing").isEmpty();
        adapter.markCompleted("tx-1", "partner-1", updatedAt);
        adapter.markRetrying("tx-2", "temporary", updatedAt);
        adapter.markFailed("tx-3", "permanent", updatedAt);

        // assert
        assertTrue(missing);
        verify(repository).markCompleted("tx-1", "partner-1", updatedAt);
        verify(repository).markRetrying("tx-2", "temporary", updatedAt);
        verify(repository).markFailed("tx-3", "permanent", updatedAt);
    }

    @Test
    void shouldMapExistingPersistenceModel() {
        // arrange
        when(repository.findByTransactionId("tx-empty"))
                .thenReturn(Optional.of(new PixTransactionPersistenceModel()));

        // act
        var transaction = new PixTransactionRepositoryAdapter(repository)
                .findByTransactionId("tx-empty").orElseThrow();

        // assert
        assertTrue(transaction.transactionId() == null && transaction.status() == null);
    }
}
