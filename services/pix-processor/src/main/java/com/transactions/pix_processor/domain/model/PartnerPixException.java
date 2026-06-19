package com.transactions.pix_processor.domain.model;

public class PartnerPixException extends RuntimeException {

    private final String errorCode;

    public PartnerPixException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
