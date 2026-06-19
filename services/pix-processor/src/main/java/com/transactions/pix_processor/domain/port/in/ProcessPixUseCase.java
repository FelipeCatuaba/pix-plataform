package com.transactions.pix_processor.domain.port.in;

import java.util.Optional;

public interface ProcessPixUseCase {

    Optional<ProcessPixResult> process(String payload);
}
