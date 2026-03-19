package com.passtheo.content.dto.response;

/**
 * Domain with user progress overlay.
 */
public record DomainWithProgressDto(
    String code,
    String name,
    String icon,
    String color,
    int topicCount,
    int questionCount,
    boolean isFreePreview,
    boolean isLocked,
    ProgressOverlay progress
) {
    /**
     * Progress overlay for a domain.
     */
    public record ProgressOverlay(
        double coveragePercent,
        double accuracyPercent,
        int masteredCount,
        String strength
    ) {}
}
