package com.passtheo.content.integration.strapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Strapi Lesson content type attributes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StrapiLessonDto(
    String title,
    String slug,
    String content,
    String summary,
    String coverImage,
    String videoUrl,
    Integer readTimeMinutes,
    boolean isActive,
    boolean isPremium,
    int sortOrder
) {}
