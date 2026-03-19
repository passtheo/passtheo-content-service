package com.passtheo.content.domain.enums;

/**
 * Readiness label derived from composite readiness score (0-100).
 */
public enum ReadinessLabel {

    /** Score below 30. */
    NOT_READY,

    /** Score 30-59. */
    GETTING_THERE,

    /** Score 60-79. */
    ALMOST_READY,

    /** Score 80+. */
    READY
}
