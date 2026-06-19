package com.transactions.pix.adapter.out.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transactions.pix.adapter.out.persistence.OutboxEvent;
import com.transactions.pix.adapter.out.persistence.OutboxRepositoryAdapter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxRepositoryAdapter repository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void shouldPublishPendingEventAndRecordMetrics() {
        // arrange
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        UUID id = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent(
                id, "tx-1", "PixRequested", "{}", "PENDING", Instant.now().minusSeconds(2), null
        );
        when(repository.findPending(50)).thenReturn(List.of(event));
        when(kafkaTemplate.send("pix.requested", "tx-1", "{}"))
                .thenReturn(CompletableFuture.completedFuture(null));

        // act
        new OutboxPublisher(repository, kafkaTemplate, registry).publishPendingEvents();

        // assert
        verify(repository).markPublished(org.mockito.ArgumentMatchers.eq(id), org.mockito.ArgumentMatchers.any());
        assertEquals(1.0, registry.counter("pix.outbox.published").count());
        assertEquals(1.0, registry.get("pix.outbox.pending").gauge().value());
    }

    @Test
    void shouldKeepEventPendingWhenKafkaPublishFails() {
        // arrange
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        UUID id = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent(
                id, "tx-2", "PixRequested", "{}", "PENDING", Instant.now().plusSeconds(2), null
        );
        when(repository.findPending(50)).thenReturn(List.of(event));
        CompletableFuture failed = CompletableFuture.failedFuture(new IllegalStateException("kafka unavailable"));
        when(kafkaTemplate.send("pix.requested", "tx-2", "{}")).thenReturn(failed);

        // act
        new OutboxPublisher(repository, kafkaTemplate, registry).publishPendingEvents();

        // assert
        verify(repository, never()).markPublished(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()
        );
        assertEquals(0.0, registry.counter("pix.outbox.published").count());
        assertEquals(1.0, registry.get("pix.outbox.pending").gauge().value());
    }
}
