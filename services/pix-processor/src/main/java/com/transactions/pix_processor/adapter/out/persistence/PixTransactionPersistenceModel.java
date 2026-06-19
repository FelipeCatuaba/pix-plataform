package com.transactions.pix_processor.adapter.out.persistence;

import com.transactions.pix_processor.domain.model.PixTransaction;
import com.transactions.pix_processor.domain.model.PixTransactionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        schema = "pix",
        name = "pix_transaction",
        uniqueConstraints = @UniqueConstraint(name = "uk_pix_transaction_transaction_id", columnNames = "transaction_id")
)
public class PixTransactionPersistenceModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, length = 100, unique = true)
    private String transactionId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "pix_key", nullable = false)
    private String pixKey;

    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PixTransactionStatus status;

    @Column(name = "partner_reference_id", length = 100)
    private String partnerReferenceId;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PixTransactionPersistenceModel() {
    }

    PixTransaction toDomain() {
        return new PixTransaction(
                id,
                transactionId,
                amount,
                pixKey,
                description,
                status,
                partnerReferenceId,
                failureReason,
                createdAt,
                updatedAt
        );
    }
}
