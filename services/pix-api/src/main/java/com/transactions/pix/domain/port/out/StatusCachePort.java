package com.transactions.pix.domain.port.out;

import com.transactions.pix.domain.model.PixTransaction;
import java.time.Duration;
import java.util.Optional;

public interface StatusCachePort {

    Optional<PixTransaction> findByTransactionId(String transactionId);

    void cache(PixTransaction transaction, Duration ttl);
}
