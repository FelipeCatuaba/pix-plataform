package com.transactions.pix.domain.port.out;

import java.time.Instant;

public interface OutboxEventPort {

    void append(String aggregateId, String eventType, String payload, Instant createdAt);
}
