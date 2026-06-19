package com.transactions.pix_processor.domain.port.out;

public interface ProcessingAttemptPort {

    int nextAttemptNumber(String transactionId);

    void insert(String transactionId, int attemptNumber, String status, String errorCode, String errorMessage);
}
