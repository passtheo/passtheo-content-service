package com.passtheo.content.integration.strapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Strapi Question content type attributes — the core question data.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StrapiQuestionDto(
    String id,
    String documentId,
    String questionText,
    String interactionType,
    String difficulty,
    String imageUrl,
    String videoUrl,
    Integer correctNumber,
    Integer correctNumberTolerance,
    Boolean correctBoolean,
    int version,
    List<AnswerOptionDto> answerOptions,
    ExplanationDto explanation,
    List<ImageRegionDto> imageRegions,
    List<DragTargetDto> dragTargets,
    String domainCode,
    String topicCode
) {

    /**
     * Answer option for multiple choice questions.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AnswerOptionDto(
        String id,
        String text,
        String image,
        boolean isCorrect,
        int sortOrder
    ) {}

    /**
     * Explanation shown after answering.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExplanationDto(
        String text,
        String image,
        String tip,
        String relatedRoadSignCode,
        String legalReference
    ) {}

    /**
     * Image region for tap_on_image questions.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImageRegionDto(
        String id,
        String label,
        double xPercent,
        double yPercent,
        double widthPercent,
        double heightPercent,
        boolean isCorrect,
        int sortOrder
    ) {}

    /**
     * Drag target for drag_checkmark and drag_numbers questions.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DragTargetDto(
        String id,
        String label,
        String correctValue,
        boolean isCorrect,
        int sortOrder,
        String image
    ) {}
}
