package com.transactions.pix.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

public record PixTransaction(
        Long id,
        String transactionId,
        BigDecimal amount,
        String pixKey,
        String description,
        PixTransactionStatus status,
        String partnerReferenceId,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
    public static PixTransaction create(
            String transactionId,
            BigDecimal amount,
            String pixKey,
            String description,
            Instant requestStartedAt
    ) {
        return new PixTransaction(
                null,
                transactionId,
                amount,
                pixKey,
                description,
                PixTransactionStatus.PROCESSING,
                null,
                null,
                requestStartedAt,
                requestStartedAt
        );
    }
}
