package com.transactions.pix.adapter.out.persistence;

import com.transactions.pix.domain.port.out.OutboxEventPort;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxRepositoryAdapter implements OutboxEventPort {

    private final OutboxEventCrudRepository repository;

    public OutboxRepositoryAdapter(OutboxEventCrudRepository repository) {
        this.repository = repository;
    }

    @Override
    public void append(String aggregateId, String eventType, String payload, Instant createdAt) {
        repository.save(OutboxEventPersistenceModel.pending(aggregateId, eventType, payload, createdAt));
    }

    public List<OutboxEvent> findPending(int limit) {
        return repository.findPendingWithLock(limit).stream()
                .map(OutboxEventPersistenceModel::toDomain)
                .toList();
    }

    public void markPublished(UUID id, Instant publishedAt) {
        OutboxEventPersistenceModel entity = repository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Outbox event not found: " + id));
        entity.markPublished(publishedAt);
        repository.save(entity);
    }
}
