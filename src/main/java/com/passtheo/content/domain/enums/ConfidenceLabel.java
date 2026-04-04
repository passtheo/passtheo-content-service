package com.passtheo.content.domain.enums;

/**
 * Confidence label derived from exam confidence score (0-95).
 * Flutter maps {@code name()} to localized strings via ARB keys.
 */
public enum ConfidenceLabel {

    /** Score below 30. */
    NOT_READY,

    /** Score 30-59. */
    GETTING_THERE,

    /** Score 60-79. */
    ALMOST_READY,

    /** Score 80+. */
    READY
}
