package com.transactions.pix.domain.port.out;

import com.transactions.pix.domain.model.PixTransaction;
import java.util.Optional;

public interface PixTransactionPort {

    PixTransaction insert(PixTransaction transaction);

    Optional<PixTransaction> findByTransactionId(String transactionId);
}
