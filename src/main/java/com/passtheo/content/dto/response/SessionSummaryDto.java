package com.passtheo.content.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Session completion summary response DTO.
 */
public record SessionSummaryDto(
    UUID sessionId,
    String status,
    int totalQuestions,
    int correctCount,
    double accuracyPercent,
    int timeSpentSeconds,
    MasteryChangesDto masteryChanges,
    StreakUpdateDto streakUpdate,
    List<EarnedAchievementDto> newAchievements
) {
    /**
     * Summary of mastery level changes during the session.
     */
    public record MasteryChangesDto(
        int upgraded,
        int downgraded,
        int unchanged
    ) {}

    /**
     * Streak update from this session.
     */
    public record StreakUpdateDto(
        int currentStreak,
        boolean isNewDay
    ) {}
}
