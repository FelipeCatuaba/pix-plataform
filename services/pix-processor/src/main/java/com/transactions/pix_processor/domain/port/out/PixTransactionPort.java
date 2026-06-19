package com.transactions.pix_processor.domain.port.out;

import com.transactions.pix_processor.domain.model.PixTransaction;
import java.time.Instant;
import java.util.Optional;

public interface PixTransactionPort {

    Optional<PixTransaction> findByTransactionId(String transactionId);

    void markCompleted(String transactionId, String partnerReferenceId, Instant updatedAt);

    void markRetrying(String transactionId, String failureReason, Instant updatedAt);

    void markFailed(String transactionId, String failureReason, Instant updatedAt);
}
