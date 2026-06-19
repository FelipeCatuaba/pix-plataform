package com.transactions.pix_processor.adapter.out.client;

import java.math.BigDecimal;

public record PartnerPixRequest(
        String transactionId,
        BigDecimal amount,
        String pixKey,
        String description
) {
}
