package com.passtheo.content.dto.response;

import jakarta.annotation.Nullable;

import java.util.Map;

/**
 * Full-content snapshot of a previously answered question within a session.
 * Included in {@link SessionDto} on resume so the Flutter client can rehydrate
 * per-question state (the user's original selection, the revealed correct
 * answer, and the explanation) and allow the user to navigate back.
 *
 * @param questionOrder the 1-based order of the question within the session
 * @param question      the full question payload (text, options, regions, ...)
 * @param userAnswer    the user's submitted answer (null when skipped)
 * @param correctAnswer the revealed correct answer payload
 * @param explanation   the explanation revealed after answering
 * @param isCorrect     whether the user's answer was correct
 * @param skipped       whether the user skipped this question
 * @param timeTakenMs   time taken by the user on this question in milliseconds
 */
public record AnsweredQuestionFullDto(
    int questionOrder,
    QuestionDto question,
    @Nullable Map<String, Object> userAnswer,
    Map<String, Object> correctAnswer,
    @Nullable AnswerResultDto.ExplanationDto explanation,
    boolean isCorrect,
    boolean skipped,
    int timeTakenMs
) {}
