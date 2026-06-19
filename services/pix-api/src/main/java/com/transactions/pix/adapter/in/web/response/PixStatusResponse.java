package com.transactions.pix.adapter.in.web.response;

import com.transactions.pix.domain.model.PixTransaction;
import com.transactions.pix.domain.model.PixTransactionStatus;
import java.time.Instant;

public record PixStatusResponse(
        String transactionId,
        PixTransactionStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static PixStatusResponse from(PixTransaction transaction) {
        return new PixStatusResponse(
                transaction.transactionId(),
                transaction.status(),
                transaction.createdAt(),
                transaction.updatedAt()
        );
    }
}
