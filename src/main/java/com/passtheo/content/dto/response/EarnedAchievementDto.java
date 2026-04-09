package com.passtheo.content.dto.response;

/**
 * Earned achievement notification DTO (compact, for inline display).
 *
 * @param code     achievement code
 * @param name     achievement name
 * @param icon     achievement icon identifier
 * @param xpReward XP awarded for this achievement
 */
public record EarnedAchievementDto(
    String code,
    String name,
    String icon,
    int xpReward
) {}
