package com.passtheo.content.integration.strapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Strapi Lesson content type attributes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StrapiLessonDto(
    String title,
    String slug,
    List<SectionDto> sections,
    String summary,
    String coverImage,
    String videoUrl,
    Integer readTimeMinutes,
    boolean isActive,
    boolean isPremium,
    int sortOrder
) {

    /**
     * A single structured section within a lesson.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SectionDto(
        String heading,
        String body,
        String tip,
        String keyRule,
        String relatedRoadSignCode,
        int sortOrder
    ) {}
}
