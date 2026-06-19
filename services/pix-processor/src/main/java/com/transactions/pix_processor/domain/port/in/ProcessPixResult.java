package com.transactions.pix_processor.domain.port.in;

import java.time.Instant;

public record ProcessPixResult(
        String transactionId,
        Instant requestStartedAt,
        String outcome
) {
}
