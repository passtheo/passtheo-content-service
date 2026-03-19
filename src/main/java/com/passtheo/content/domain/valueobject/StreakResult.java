package com.passtheo.content.domain.valueobject;

import java.time.LocalDate;

/**
 * Result of a streak update operation.
 *
 * @param currentStreak        current consecutive study days
 * @param longestStreak        all-time longest streak
 * @param totalStudyDays       total days the student has studied
 * @param lastStudyDate        date of last study activity
 * @param freezeSlotsAvailable available freeze slots
 * @param freezeSlotsUsed      total freeze slots used
 * @param studiedToday         whether the student studied today
 * @param streakAtRisk         true if streak will break tomorrow without study
 * @param isNewDay             whether this was a new study day
 */
public record StreakResult(
    int currentStreak,
    int longestStreak,
    int totalStudyDays,
    LocalDate lastStudyDate,
    int freezeSlotsAvailable,
    int freezeSlotsUsed,
    boolean studiedToday,
    boolean streakAtRisk,
    boolean isNewDay
) {}
