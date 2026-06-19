package com.transactions.pix.adapter.out.persistence;

import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

public interface PixTransactionCrudRepository extends CrudRepository<PixTransactionPersistenceModel, Long> {

    Optional<PixTransactionPersistenceModel> findByTransactionId(String transactionId);
}
