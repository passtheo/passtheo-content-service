package com.passtheo.content.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Session state response DTO.
 *
 * <p>On resume ({@code GET /api/practice/sessions/:id}):
 * <ul>
 *   <li>{@code answeredQuestions} is a lightweight summary of every previously
 *       answered question so the client can colour the navigator grid.</li>
 *   <li>{@code answeredQuestionContents} is the full content of each previously
 *       answered question (question payload, user's answer, revealed correct
 *       answer, explanation) so the client can rehydrate per-question state
 *       and allow the user to navigate back.</li>
 * </ul>
 *
 * <p><b>Slot alignment:</b> {@code answeredQuestionContents[i].questionOrder}
 * (1-based) is the authoritative slot index — not the array position. Items
 * whose underlying Strapi question has been deactivated or deleted are omitted,
 * so {@code answeredQuestionContents.size()} may be less than
 * {@code answeredCount}. Clients must place each entry at
 * {@code questionOrder - 1} and leave any resulting gap as an unreachable
 * "locked" slot. {@code answeredQuestions} always has one summary per real
 * answer ({@code size() == answeredCount}).
 */
public record SessionDto(
    UUID sessionId,
    String status,
    int totalQuestions,
    int answeredCount,
    int correctCount,
    QuestionDto currentQuestion,
    List<AnsweredQuestionSummaryDto> answeredQuestions,
    List<AnsweredQuestionFullDto> answeredQuestionContents
) {}
