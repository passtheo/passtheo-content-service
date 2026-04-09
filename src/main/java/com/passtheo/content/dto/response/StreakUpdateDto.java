package com.passtheo.content.dto.response;

/**
 * Streak update from a study activity (practice session or exam).
 *
 * @param currentStreak the current streak count
 * @param isNewDay      whether this activity started a new streak day
 */
public record StreakUpdateDto(
    int currentStreak,
    boolean isNewDay
) {}
