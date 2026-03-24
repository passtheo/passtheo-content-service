package com.passtheo.content.integration.strapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Strapi 5 Question content type — flat format (no nested attributes wrapper).
 * Relations (domain, topic, roadSigns) are inline objects when populated.
 * Components (answerOptions, explanation, imageRegions, dragTargets) are inline arrays/objects.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StrapiQuestionDto(
    int id,
    String documentId,
    String questionText,
    String interactionType,
    String difficulty,
    String videoUrl,
    Integer correctNumber,
    Integer correctNumberTolerance,
    Boolean correctBoolean,
    int version,
    boolean isActive,
    boolean isPremium,
    String reviewStatus,
    String cbrReference,

    // Components (inline, not wrapped in data)
    List<AnswerOptionDto> answerOptions,
    ExplanationDto explanation,
    List<ImageRegionDto> imageRegions,
    List<DragTargetDto> dragTargets,

    // Media (populated with fields[0]=url)
    StrapiMediaDto image,
    StrapiMediaDto video,

    // Relations (inline objects when populated)
    StrapiRelationDto domain,
    StrapiRelationDto topic,
    List<StrapiRelationDto> roadSigns
) {

    /**
     * Answer option for multiple choice questions.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AnswerOptionDto(
        int id,
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
        int id,
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
        int id,
        String label,
        String correctValue,
        boolean isCorrect,
        int sortOrder,
        String image
    ) {}
}
