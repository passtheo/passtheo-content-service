package com.passtheo.content.domain.enums;

/**
 * Strength classification for a domain based on accuracy and coverage.
 * Thresholds: WEAK (<50%), MODERATE (50-70%), STRONG (70-85%), MASTERED (≥85% + ≥80% coverage).
 */
public enum DomainStrength {

    /** Not enough data to classify. */
    UNKNOWN,

    /** Accuracy below 50%. */
    WEAK,

    /** Accuracy 50-70%. */
    MODERATE,

    /** Accuracy 70-85% with ≥60% coverage. */
    STRONG,

    /** Accuracy ≥85% with ≥80% coverage. */
    MASTERED
}
