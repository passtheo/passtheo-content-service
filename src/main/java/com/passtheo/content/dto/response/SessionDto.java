package com.passtheo.content.dto.response;

import java.util.UUID;

/**
 * Session state response DTO.
 */
public record SessionDto(
    UUID sessionId,
    String status,
    int totalQuestions,
    int answeredCount,
    int correctCount,
    QuestionDto currentQuestion
) {}
