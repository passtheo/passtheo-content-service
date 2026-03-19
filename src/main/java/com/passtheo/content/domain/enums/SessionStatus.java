package com.passtheo.content.domain.enums;

/**
 * Status of a study session.
 */
public enum SessionStatus {

    /** Session is active — student is answering questions. */
    IN_PROGRESS,

    /** Session was completed normally. */
    COMPLETED,

    /** Session was abandoned (no activity for 24h). */
    ABANDONED
}
