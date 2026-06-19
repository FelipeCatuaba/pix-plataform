package com.transactions.pix_processor.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface PixTransactionCrudRepository extends CrudRepository<PixTransactionPersistenceModel, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PixTransactionPersistenceModel> findByTransactionId(String transactionId);

    @Modifying
    @Query("""
            UPDATE PixTransactionPersistenceModel tx
            SET tx.status = com.transactions.pix_processor.domain.model.PixTransactionStatus.COMPLETED,
                tx.partnerReferenceId = :partnerReferenceId,
                tx.failureReason = null,
                tx.updatedAt = :updatedAt
            WHERE tx.transactionId = :transactionId
            """)
    void markCompleted(
            @Param("transactionId") String transactionId,
            @Param("partnerReferenceId") String partnerReferenceId,
            @Param("updatedAt") Instant updatedAt
    );

    @Modifying
    @Query("""
            UPDATE PixTransactionPersistenceModel tx
            SET tx.status = com.transactions.pix_processor.domain.model.PixTransactionStatus.RETRYING,
                tx.failureReason = :failureReason,
                tx.updatedAt = :updatedAt
            WHERE tx.transactionId = :transactionId
            """)
    void markRetrying(
            @Param("transactionId") String transactionId,
            @Param("failureReason") String failureReason,
            @Param("updatedAt") Instant updatedAt
    );

    @Modifying
    @Query("""
            UPDATE PixTransactionPersistenceModel tx
            SET tx.status = com.transactions.pix_processor.domain.model.PixTransactionStatus.FAILED,
                tx.failureReason = :failureReason,
                tx.updatedAt = :updatedAt
            WHERE tx.transactionId = :transactionId
            """)
    void markFailed(
            @Param("transactionId") String transactionId,
            @Param("failureReason") String failureReason,
            @Param("updatedAt") Instant updatedAt
    );
}
