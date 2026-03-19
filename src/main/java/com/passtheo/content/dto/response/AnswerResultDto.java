package com.passtheo.content.dto.response;

import java.util.List;
import java.util.Map;

/**
 * Answer result response DTO — returned after submitting an answer.
 */
public record AnswerResultDto(
    boolean isCorrect,
    Map<String, Object> correctAnswer,
    ExplanationDto explanation,
    MasteryUpdateDto masteryUpdate,
    SessionProgressDto sessionProgress,
    QuestionDto nextQuestion,
    List<EarnedAchievementDto> newAchievements
) {
    /**
     * Explanation for the correct answer.
     */
    public record ExplanationDto(
        String text,
        String tip,
        String imageUrl,
        String legalReference
    ) {}

    /**
     * Mastery level change.
     */
    public record MasteryUpdateDto(
        String previousLevel,
        String newLevel,
        int consecutiveCorrect
    ) {}

    /**
     * Session progress snapshot.
     */
    public record SessionProgressDto(
        int answeredCount,
        int correctCount,
        int totalQuestions,
        double accuracyPercent
    ) {}
}
