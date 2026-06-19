package com.transactions.pix.adapter.out.cache;

import com.transactions.pix.domain.model.PixTransaction;
import com.transactions.pix.domain.model.PixTransactionStatus;
import java.time.Instant;

public record CachedPixStatus(
        String transactionId,
        PixTransactionStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    static CachedPixStatus from(PixTransaction transaction) {
        return new CachedPixStatus(
                transaction.transactionId(),
                transaction.status(),
                transaction.createdAt(),
                transaction.updatedAt()
        );
    }

    PixTransaction toTransaction() {
        return new PixTransaction(
                null,
                transactionId,
                null,
                null,
                null,
                status,
                null,
                null,
                createdAt,
                updatedAt
        );
    }
}
