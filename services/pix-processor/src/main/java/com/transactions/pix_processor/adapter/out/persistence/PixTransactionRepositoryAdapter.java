package com.transactions.pix_processor.adapter.out.persistence;

import com.transactions.pix_processor.domain.model.PixTransaction;
import com.transactions.pix_processor.domain.port.out.PixTransactionPort;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class PixTransactionRepositoryAdapter implements PixTransactionPort {

    private final PixTransactionCrudRepository repository;

    public PixTransactionRepositoryAdapter(PixTransactionCrudRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<PixTransaction> findByTransactionId(String transactionId) {
        return repository.findByTransactionId(transactionId).map(PixTransactionPersistenceModel::toDomain);
    }

    @Override
    public void markCompleted(String transactionId, String partnerReferenceId, Instant updatedAt) {
        repository.markCompleted(transactionId, partnerReferenceId, updatedAt);
    }

    @Override
    public void markRetrying(String transactionId, String failureReason, Instant updatedAt) {
        repository.markRetrying(transactionId, failureReason, updatedAt);
    }

    @Override
    public void markFailed(String transactionId, String failureReason, Instant updatedAt) {
        repository.markFailed(transactionId, failureReason, updatedAt);
    }
}
