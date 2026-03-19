package com.passtheo.content.repository;

import com.passtheo.content.domain.entity.OutboxEvent;
import com.passtheo.content.domain.enums.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for OutboxEvent entities.
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Finds pending outbox events ordered by creation time for polling.
     *
     * @param status the outbox status to filter by
     * @return list of pending events
     */
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
