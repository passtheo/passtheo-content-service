package com.passtheo.content.dto.response;

import jakarta.annotation.Nullable;
import java.util.List;

/**
 * Question response DTO (sent to Flutter, never includes correct answer).
 */
public record QuestionDto(
    String strapiQuestionId,
    String questionText,
    String interactionType,
    @Nullable String difficulty,
    String imageUrl,
    String videoUrl,
    List<AnswerOptionDto> answerOptions,
    List<ImageRegionDto> imageRegions,
    List<DragTargetDto> dragTargets,
    int questionOrder,
    String domainCode,
    ExplanationDto explanation
) {
    /**
     * Answer option (without isCorrect).
     */
    public record AnswerOptionDto(
        String id,
        String text,
        String imageUrl
    ) {}

    /**
     * Image region (without isCorrect).
     */
    public record ImageRegionDto(
        String id,
        double xPercent,
        double yPercent,
        double widthPercent,
        double heightPercent
    ) {}

    /**
     * Drag target (without correctValue/isCorrect).
     */
    public record DragTargetDto(
        String id,
        String label,
        String imageUrl
    ) {}

    /**
     * Explanation shown via Reveal Hint (tip) and after answering.
     * Included in the question payload so the tip is available before answering.
     */
    public record ExplanationDto(
        String text,
        String tip,
        String legalReference,
        String imageUrl
    ) {}
}
