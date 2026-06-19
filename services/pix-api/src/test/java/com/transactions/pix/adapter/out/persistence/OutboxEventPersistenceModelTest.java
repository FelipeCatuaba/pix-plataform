package com.transactions.pix.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class OutboxEventPersistenceModelTest {

    @Test
    void shouldCreatePendingEventAndMarkItPublished() {
        // arrange
        Instant createdAt = Instant.parse("2026-06-19T12:00:00Z");
        Instant publishedAt = createdAt.plusSeconds(2);

        // act
        OutboxEventPersistenceModel model =
                OutboxEventPersistenceModel.pending("tx-1", "PixRequested", "{}", createdAt);
        OutboxEvent pending = model.toDomain();
        model.markPublished(publishedAt);
        OutboxEvent published = model.toDomain();

        // assert
        assertNotNull(pending.id());
        assertEquals("PENDING", pending.status());
        assertNull(pending.publishedAt());
        assertEquals("PUBLISHED", published.status());
        assertEquals(publishedAt, published.publishedAt());
    }
}
