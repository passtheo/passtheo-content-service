package com.passtheo.content.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Full session breakdown response for question-by-question review.
 */
public record SessionBreakdownDto(
    UUID sessionId,
    String status,
    int totalQuestions,
    int correctCount,
    int skippedCount,
    double accuracyPercent,
    int timeSpentSeconds,
    SessionSummaryDto.MasteryChangesDto masteryChanges,
    List<BreakdownQuestionDto> questions
) {}
