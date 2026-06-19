package com.transactions.pix_processor.adapter.out.persistence;

import com.transactions.pix_processor.domain.port.out.ProcessingAttemptPort;
import java.time.Instant;
import org.springframework.stereotype.Repository;

@Repository
public class ProcessingAttemptRepositoryAdapter implements ProcessingAttemptPort {

    private final ProcessingAttemptCrudRepository repository;

    public ProcessingAttemptRepositoryAdapter(ProcessingAttemptCrudRepository repository) {
        this.repository = repository;
    }

    @Override
    public int nextAttemptNumber(String transactionId) {
        return repository.nextAttemptNumber(transactionId);
    }

    @Override
    public void insert(String transactionId, int attemptNumber, String status, String errorCode, String errorMessage) {
        repository.save(ProcessingAttemptPersistenceModel.create(
                transactionId,
                attemptNumber,
                status,
                errorCode,
                errorMessage,
                Instant.now()
        ));
    }
}
