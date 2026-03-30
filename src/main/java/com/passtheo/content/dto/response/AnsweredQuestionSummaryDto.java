package com.passtheo.content.dto.response;

/**
 * Summary of a previously answered question within a session.
 * Included in {@link SessionDto} on resume so the Flutter client can
 * reconstruct the question navigator grid state (correct / wrong / skipped).
 */
public record AnsweredQuestionSummaryDto(
    int questionOrder,
    String strapiQuestionId,
    boolean correct,
    boolean skipped
) {}
