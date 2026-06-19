package com.transactions.pix.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface OutboxEventCrudRepository extends CrudRepository<OutboxEventPersistenceModel, UUID> {

    @Query(value = """
            SELECT *
            FROM pix.outbox_event
            WHERE status = 'PENDING'
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventPersistenceModel> findPendingWithLock(@Param("limit") int limit);
}
