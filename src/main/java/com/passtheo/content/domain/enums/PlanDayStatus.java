package com.passtheo.content.domain.enums;

/**
 * Status of a single day in a study plan.
 */
public enum PlanDayStatus {

    /** Day not yet started. */
    PENDING,

    /** Day in progress. */
    IN_PROGRESS,

    /** Day completed. */
    COMPLETED,

    /** Day was skipped. */
    SKIPPED
}
