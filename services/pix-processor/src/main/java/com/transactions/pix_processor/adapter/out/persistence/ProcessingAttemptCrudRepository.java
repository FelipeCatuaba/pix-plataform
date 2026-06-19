package com.transactions.pix_processor.adapter.out.persistence;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface ProcessingAttemptCrudRepository extends CrudRepository<ProcessingAttemptPersistenceModel, Long> {

    @Query("""
            SELECT COALESCE(MAX(attempt.attemptNumber), 0) + 1
            FROM ProcessingAttemptPersistenceModel attempt
            WHERE attempt.transactionId = :transactionId
            """)
    int nextAttemptNumber(@Param("transactionId") String transactionId);
}
