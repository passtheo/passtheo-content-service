package com.passtheo.content.domain.entity;

import com.passtheo.content.domain.enums.OutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbox event for reliable Kafka publishing.
 * Written atomically with business operations, polled and published by OutboxPoller.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    private static final int MAX_RETRY_COUNT = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "topic", nullable = false)
    private String topic;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "error_message")
    private String errorMessage;

    public OutboxEvent() {
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    /**
     * Marks the event as successfully sent to Kafka.
     */
    public void markSent() {
        this.status = OutboxStatus.SENT;
        this.sentAt = Instant.now();
    }

    /**
     * Records a send failure and increments retry count.
     *
     * @param error description of the failure
     */
    public void markFailed(String error) {
        this.retryCount++;
        this.failedAt = Instant.now();
        this.errorMessage = error;
        if (this.retryCount >= MAX_RETRY_COUNT) {
            this.status = OutboxStatus.FAILED;
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public OutboxStatus getStatus() { return status; }
    public void setStatus(OutboxStatus status) { this.status = status; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
    public Instant getFailedAt() { return failedAt; }
    public void setFailedAt(Instant failedAt) { this.failedAt = failedAt; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
