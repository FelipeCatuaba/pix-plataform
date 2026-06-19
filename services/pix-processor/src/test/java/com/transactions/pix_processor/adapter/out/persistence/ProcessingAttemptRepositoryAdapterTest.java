package com.transactions.pix_processor.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessingAttemptRepositoryAdapterTest {

    @Mock
    private ProcessingAttemptCrudRepository repository;

    @Test
    void shouldDelegateAttemptNumberAndInsert() {
        // arrange
        when(repository.nextAttemptNumber("tx-1")).thenReturn(3);
        ProcessingAttemptRepositoryAdapter adapter = new ProcessingAttemptRepositoryAdapter(repository);

        // act
        int attempt = adapter.nextAttemptNumber("tx-1");
        adapter.insert("tx-1", attempt, "FAILED", "PARTNER_ERROR", "failed");

        // assert
        assertEquals(3, attempt);
        verify(repository).save(org.mockito.ArgumentMatchers.any(ProcessingAttemptPersistenceModel.class));
    }
}
