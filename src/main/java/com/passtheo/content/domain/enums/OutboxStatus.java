package com.passtheo.content.domain.enums;

/**
 * Status of outbox events for Kafka publishing.
 */
public enum OutboxStatus {

    /** Event is pending Kafka publishing. */
    PENDING,

    /** Event has been successfully sent to Kafka. */
    SENT,

    /** Event failed to send after max retries. */
    FAILED
}
