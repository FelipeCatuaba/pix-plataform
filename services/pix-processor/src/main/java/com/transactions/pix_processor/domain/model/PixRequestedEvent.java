package com.transactions.pix_processor.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

public record PixRequestedEvent(
        String transactionId,
        BigDecimal amount,
        String pixKey,
        String description,
        Instant createdAt,
        String correlationId
) {
}
