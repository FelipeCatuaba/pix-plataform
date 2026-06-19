package com.transactions.pix_processor.domain.model;

public enum PixTransactionStatus {
    PROCESSING,
    RETRYING,
    COMPLETED,
    FAILED
}
