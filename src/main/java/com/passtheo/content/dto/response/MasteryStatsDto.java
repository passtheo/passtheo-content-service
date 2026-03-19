package com.passtheo.content.dto.response;

/**
 * Mastery level distribution (pie chart data).
 */
public record MasteryStatsDto(
    int totalQuestions,
    int newCount,
    int learning,
    int familiar,
    int mastered,
    double newPercent,
    double learningPercent,
    double familiarPercent,
    double masteredPercent
) {}
