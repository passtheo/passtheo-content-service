package com.passtheo.content.dto.response;

import java.time.Instant;

/**
 * Achievement gallery item (earned + locked).
 */
public record AchievementDto(
    String code,
    String name,
    String description,
    String icon,
    boolean isEarned,
    Instant earnedAt,
    String triggerType,
    int triggerValue,
    int currentProgress,
    double progressPercent
) {}
