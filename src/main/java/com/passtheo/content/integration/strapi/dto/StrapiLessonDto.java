package com.passtheo.content.integration.strapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Strapi Lesson content type attributes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StrapiLessonDto(
    int id,
    String documentId,
    String title,
    String slug,
    List<SectionDto> sections,
    String summary,
    String coverImage,
    String videoUrl,
    Integer readTimeMinutes,
    boolean isActive,
    boolean isPremium,
    Integer sortOrder
) {

    /**
     * A single structured section within a lesson.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SectionDto(
        String heading,
        String body,
        String tip,
        String warning,
        String keyRule,
        SectionImageDto image,
        List<RoadSignRefDto> roadSigns
    ) {}

    /**
     * Nested image-with-metadata component inside a lesson section.
     * Mirrors the `lesson.section-image` component in Strapi.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SectionImageDto(
        StrapiMediaDto file,
        String caption,
        String alt
    ) {}

    /**
     * Trimmed road-sign reference populated alongside a lesson section's
     * {@code roadSigns} relation. Only the identifying fields are kept — the
     * client can resolve the full sign by {@code code} via the road-sign
     * collection endpoint when needed.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RoadSignRefDto(
        int id,
        String documentId,
        String code,
        String name
    ) {}
}
