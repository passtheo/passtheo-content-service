package com.passtheo.content.dto.response;

import java.time.LocalDate;

/**
 * Streak status response DTO.
 */
public record StreakDto(
    int currentStreak,
    int longestStreak,
    int totalStudyDays,
    LocalDate lastStudyDate,
    int freezeSlotsAvailable,
    int freezeSlotsUsed,
    boolean studiedToday,
    boolean streakAtRisk
) {}
