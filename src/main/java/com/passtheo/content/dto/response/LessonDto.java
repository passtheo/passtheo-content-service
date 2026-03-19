package com.passtheo.content.dto.response;

/**
 * Lesson response DTO.
 */
public record LessonDto(
    String title,
    String slug,
    String content,
    String summary,
    String coverImage,
    String videoUrl,
    Integer readTimeMinutes
) {}
