package com.passtheo.content.domain.enums;

/**
 * Machine-readable recommendation key derived from exam confidence score.
 * Flutter maps {@code name()} to localized recommendation strings via ARB keys.
 */
public enum RecommendationKey {

    /** Score below 40 — user needs more practice. */
    KEEP_PRACTICING,

    /** Score 40-59 — user should focus on weak domains. */
    FOCUS_WEAK_DOMAINS,

    /** Score 60-79 — user should pass more mock exams. */
    PASS_MORE_EXAMS,

    /** Score 80+ — user is ready to book the real exam. */
    BOOK_YOUR_EXAM
}
