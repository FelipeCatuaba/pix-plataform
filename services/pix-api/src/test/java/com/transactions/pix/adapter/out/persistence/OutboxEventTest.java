package com.transactions.pix.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxEventTest {

    @Test
    void shouldExposeOutboxValues() {
        // arrange
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-06-19T12:00:00Z");

        // act
        OutboxEvent event = new OutboxEvent(id, "tx-1", "PixRequested", "{}", "PENDING", createdAt, null);

        // assert
        assertEquals(id, event.id());
        assertEquals("tx-1", event.aggregateId());
        assertEquals("PENDING", event.status());
    }
}
