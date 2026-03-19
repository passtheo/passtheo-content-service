package com.passtheo.content.dto.response;

/**
 * Earned achievement notification DTO (compact, for inline display).
 */
public record EarnedAchievementDto(
    String code,
    String name,
    String icon
) {}
