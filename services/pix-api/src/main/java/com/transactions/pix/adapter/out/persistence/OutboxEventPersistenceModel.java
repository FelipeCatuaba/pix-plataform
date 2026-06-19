package com.transactions.pix.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(schema = "pix", name = "outbox_event")
public class OutboxEventPersistenceModel {

    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEventPersistenceModel() {
    }

    static OutboxEventPersistenceModel pending(String aggregateId, String eventType, String payload, Instant createdAt) {
        OutboxEventPersistenceModel entity = new OutboxEventPersistenceModel();
        entity.id = UUID.randomUUID();
        entity.aggregateId = aggregateId;
        entity.eventType = eventType;
        entity.payload = payload;
        entity.status = "PENDING";
        entity.createdAt = createdAt;
        return entity;
    }

    OutboxEvent toDomain() {
        return new OutboxEvent(id, aggregateId, eventType, payload, status, createdAt, publishedAt);
    }

    void markPublished(Instant publishedAt) {
        this.status = "PUBLISHED";
        this.publishedAt = publishedAt;
    }
}
