package com.transactions.pix_processor.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(schema = "pix", name = "pix_processing_attempt")
public class ProcessingAttemptPersistenceModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, length = 100)
    private String transactionId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProcessingAttemptPersistenceModel() {
    }

    static ProcessingAttemptPersistenceModel create(
            String transactionId,
            int attemptNumber,
            String status,
            String errorCode,
            String errorMessage,
            Instant createdAt
    ) {
        ProcessingAttemptPersistenceModel model = new ProcessingAttemptPersistenceModel();
        model.transactionId = transactionId;
        model.attemptNumber = attemptNumber;
        model.status = status;
        model.errorCode = errorCode;
        model.errorMessage = errorMessage;
        model.createdAt = createdAt;
        return model;
    }
}
