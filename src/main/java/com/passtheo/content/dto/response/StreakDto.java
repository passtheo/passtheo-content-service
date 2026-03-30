package com.passtheo.content.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * Streak status response DTO.
 *
 * @param lastSevenDays ordered oldest-to-newest (index 0 = 6 days ago, index 6 = today);
 *                      true if the user answered at least one question on that day
 */
public record StreakDto(
    int currentStreak,
    int longestStreak,
    int totalStudyDays,
    LocalDate lastStudyDate,
    int freezeSlotsAvailable,
    int freezeSlotsUsed,
    boolean studiedToday,
    boolean streakAtRisk,
    List<Boolean> lastSevenDays
) {}
