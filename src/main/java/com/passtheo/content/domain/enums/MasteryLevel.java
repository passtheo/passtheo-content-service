package com.passtheo.content.domain.enums;

/**
 * Mastery level for spaced repetition question progress.
 * Transitions: NEW → LEARNING → FAMILIAR → MASTERED.
 */
public enum MasteryLevel {

    /** Never seen by the student. */
    NEW,

    /** Seen but not retained — 0-1 correct in a row. */
    LEARNING,

    /** Recalled with effort — 2+ correct in a row. */
    FAMILIAR,

    /** Recalled easily — 4+ correct in a row. */
    MASTERED
}
