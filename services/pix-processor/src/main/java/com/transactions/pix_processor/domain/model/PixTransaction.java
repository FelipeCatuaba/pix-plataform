package com.transactions.pix_processor.domain.model;

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
    public boolean canBeProcessed() {
        return status == PixTransactionStatus.PROCESSING || status == PixTransactionStatus.RETRYING;
    }
}
