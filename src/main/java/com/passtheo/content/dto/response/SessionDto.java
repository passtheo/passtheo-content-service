package com.passtheo.content.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Session state response DTO.
 * On resume ({@code GET /api/practice/sessions/:id}), {@code answeredQuestions} contains
 * a summary of every previously answered question so the Flutter client can
 * reconstruct the navigator grid state (correct / wrong / skipped per slot).
 */
public record SessionDto(
    UUID sessionId,
    String status,
    int totalQuestions,
    int answeredCount,
    int correctCount,
    QuestionDto currentQuestion,
    List<AnsweredQuestionSummaryDto> answeredQuestions
) {}
