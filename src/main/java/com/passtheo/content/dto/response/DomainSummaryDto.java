package com.passtheo.content.dto.response;

/**
 * Domain summary for the practice domain grid.
 * Includes Strapi metadata and the user's mastery breakdown
 * (mastered / learning / new question counts).
 */
public record DomainSummaryDto(
    String code,
    String name,
    String emoji,
    String color,
    int totalQuestions,
    int masteredCount,
    int learningCount,
    int newCount,
    double masteryPercent,
    boolean isLocked
) {}
