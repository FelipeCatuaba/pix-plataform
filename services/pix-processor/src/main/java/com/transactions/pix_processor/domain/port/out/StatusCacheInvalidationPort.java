package com.transactions.pix_processor.domain.port.out;

public interface StatusCacheInvalidationPort {

    void invalidate(String transactionId);
}
