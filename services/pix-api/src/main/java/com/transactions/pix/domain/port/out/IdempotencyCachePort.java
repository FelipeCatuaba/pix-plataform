package com.transactions.pix.domain.port.out;

import com.transactions.pix.domain.model.PixTransactionStatus;
import java.time.Duration;
import java.util.Optional;

public interface IdempotencyCachePort {

    Optional<PixTransactionStatus> findStatus(String transactionId);

    void cacheStatus(String transactionId, PixTransactionStatus status, Duration ttl);
}
