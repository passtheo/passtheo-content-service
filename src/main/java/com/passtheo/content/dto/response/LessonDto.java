package com.passtheo.content.dto.response;

import java.util.List;

/**
 * Lesson response DTO.
 */
public record LessonDto(
    String title,
    String slug,
    List<LessonSectionDto> sections,
    String summary,
    String coverImage,
    String videoUrl,
    Integer readTimeMinutes
) {

    /**
     * A structured section within a lesson.
     */
    public record LessonSectionDto(
        String heading,
        String body,
        String tip,
        String keyRule,
        String relatedRoadSignCode,
        int sortOrder
    ) {}
}
