package com.passtheo.content.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Exam history summary for listing past attempts.
 */
public record ExamHistorySummaryDto(
    UUID examId,
    boolean passed,
    int correctCount,
    int totalQuestions,
    double scorePercent,
    Instant completedAt
) {}
