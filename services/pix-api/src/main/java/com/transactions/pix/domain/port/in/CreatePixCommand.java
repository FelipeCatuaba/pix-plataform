package com.transactions.pix.domain.port.in;

import java.math.BigDecimal;
import java.time.Instant;

public record CreatePixCommand(
        String transactionId,
        BigDecimal amount,
        String pixKey,
        String description,
        String correlationId,
        Instant requestStartedAt
) {
}
