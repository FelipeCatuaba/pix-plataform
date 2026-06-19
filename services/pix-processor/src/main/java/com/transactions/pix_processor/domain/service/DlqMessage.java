package com.transactions.pix_processor.domain.service;

import java.time.Instant;

public record DlqMessage(
        String transactionId,
        int attempts,
        String lastError,
        String errorMessage,
        String originalPayload,
        Instant timestamp,
        boolean failedStatusApplied
) {
}
