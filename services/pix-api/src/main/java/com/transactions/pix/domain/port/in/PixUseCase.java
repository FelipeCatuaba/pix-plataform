package com.transactions.pix.domain.port.in;

import com.transactions.pix.domain.model.PixTransaction;

public interface PixUseCase {

    PixTransaction create(CreatePixCommand command);

    PixTransaction findByTransactionId(String transactionId);
}
