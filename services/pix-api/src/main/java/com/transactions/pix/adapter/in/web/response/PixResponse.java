package com.transactions.pix.adapter.in.web.response;

import com.transactions.pix.domain.model.PixTransaction;
import com.transactions.pix.domain.model.PixTransactionStatus;
import java.time.Instant;

public record PixResponse(
        String transactionId,
        PixTransactionStatus status,
        Instant createdAt
) {
    public static PixResponse from(PixTransaction transaction) {
        return new PixResponse(
                transaction.transactionId(),
                transaction.status(),
                transaction.createdAt()
        );
    }
}
