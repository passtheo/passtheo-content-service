package com.passtheo.content.dto.response;

import java.time.Instant;

/**
 * Topic with user progress overlay.
 */
public record TopicWithProgressDto(
    String code,
    String name,
    String difficulty,
    int questionCount,
    TopicProgressOverlay progress
) {
    /**
     * Progress overlay for a topic.
     *
     * @param coveragePercent  percentage of questions attempted (0-100)
     * @param accuracyPercent  percentage of correct answers (0-100)
     * @param masteredCount    number of questions at MASTERED level
     * @param lastPracticed    most recent answer timestamp, or null if never practiced
     */
    public record TopicProgressOverlay(
        double coveragePercent,
        double accuracyPercent,
        int masteredCount,
        Instant lastPracticed
    ) {}
}
