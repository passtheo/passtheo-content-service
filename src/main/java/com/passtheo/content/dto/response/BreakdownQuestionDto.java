package com.passtheo.content.dto.response;

import java.util.Map;

/**
 * Per-question detail in the session breakdown.
 */
public record BreakdownQuestionDto(
    int questionOrder,
    String strapiQuestionId,
    String interactionType,
    boolean isCorrect,
    boolean isSkipped,
    Map<String, Object> userAnswer,
    Map<String, Object> correctAnswer,
    Map<String, Object> questionSnapshot,
    String domainCode,
    String domainName,
    String previousMasteryLevel,
    String newMasteryLevel,
    boolean isFlagged
) {}
