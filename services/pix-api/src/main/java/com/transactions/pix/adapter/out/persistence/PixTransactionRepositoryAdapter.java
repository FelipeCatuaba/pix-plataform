package com.transactions.pix.adapter.out.persistence;

import com.transactions.pix.domain.model.PixTransaction;
import com.transactions.pix.domain.port.out.PixTransactionPort;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class PixTransactionRepositoryAdapter implements PixTransactionPort {

    private final PixTransactionCrudRepository repository;
    private final EntityManager entityManager;

    public PixTransactionRepositoryAdapter(PixTransactionCrudRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    public PixTransaction insert(PixTransaction transaction) {
        PixTransactionPersistenceModel saved = repository.save(PixTransactionPersistenceModel.fromDomain(transaction));
        entityManager.flush();
        return saved.toDomain();
    }

    @Override
    public Optional<PixTransaction> findByTransactionId(String transactionId) {
        return repository.findByTransactionId(transactionId).map(PixTransactionPersistenceModel::toDomain);
    }
}
