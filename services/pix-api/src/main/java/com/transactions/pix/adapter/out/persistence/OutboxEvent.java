package com.transactions.pix.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

public record OutboxEvent(
        UUID id,
        String aggregateId,
        String eventType,
        String payload,
        String status,
        Instant createdAt,
        Instant publishedAt
) {
}
