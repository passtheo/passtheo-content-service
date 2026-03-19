package com.passtheo.content.dto.response;

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
     */
    public record TopicProgressOverlay(
        double coveragePercent,
        double accuracyPercent,
        int masteredCount
    ) {}
}
