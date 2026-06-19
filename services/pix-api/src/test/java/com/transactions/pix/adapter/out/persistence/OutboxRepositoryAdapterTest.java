package com.transactions.pix.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxRepositoryAdapterTest {

    @Mock
    private OutboxEventCrudRepository repository;

    @Test
    void shouldAppendAndFindPendingEvents() {
        // arrange
        Instant createdAt = Instant.parse("2026-06-19T12:00:00Z");
        OutboxEventPersistenceModel model =
                OutboxEventPersistenceModel.pending("tx-1", "PixRequested", "{}", createdAt);
        when(repository.findPendingWithLock(10)).thenReturn(List.of(model));
        OutboxRepositoryAdapter adapter = new OutboxRepositoryAdapter(repository);

        // act
        adapter.append("tx-2", "PixRequested", "{}", createdAt);
        List<OutboxEvent> pending = adapter.findPending(10);

        // assert
        assertEquals("tx-1", pending.getFirst().aggregateId());
        verify(repository).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldMarkExistingEventPublished() {
        // arrange
        UUID id = UUID.randomUUID();
        Instant publishedAt = Instant.parse("2026-06-19T12:00:02Z");
        OutboxEventPersistenceModel model =
                OutboxEventPersistenceModel.pending("tx-1", "PixRequested", "{}", publishedAt.minusSeconds(2));
        when(repository.findById(id)).thenReturn(Optional.of(model));
        OutboxRepositoryAdapter adapter = new OutboxRepositoryAdapter(repository);

        // act
        adapter.markPublished(id, publishedAt);

        // assert
        ArgumentCaptor<OutboxEventPersistenceModel> saved =
                ArgumentCaptor.forClass(OutboxEventPersistenceModel.class);
        verify(repository).save(saved.capture());
        assertEquals("PUBLISHED", saved.getValue().toDomain().status());
    }

    @Test
    void shouldFailWhenOutboxEventDoesNotExist() {
        // arrange
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        // act
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> new OutboxRepositoryAdapter(repository).markPublished(id, Instant.now())
        );

        // assert
        assertEquals("Outbox event not found: " + id, error.getMessage());
    }
}
