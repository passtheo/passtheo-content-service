package com.passtheo.content.dto.response;

import java.util.List;

/**
 * Lesson response DTO.
 *
 * @param title           lesson title (localized)
 * @param slug            stable lesson slug (not localized)
 * @param sections        structured sections — empty when {@code locked=true}
 * @param summary         short summary — null when {@code locked=true}
 * @param coverImage      cover image URL — null when {@code locked=true}
 * @param videoUrl        optional video URL — null when {@code locked=true}
 * @param readTimeMinutes estimated reading time
 * @param isPremium       true if the lesson is flagged premium in Strapi
 * @param locked          true if content was stripped for the calling user
 *                        (free tier tapping a premium lesson) — client renders
 *                        a lock and opens the upgrade paywall
 */
public record LessonDto(
    String title,
    String slug,
    List<LessonSectionDto> sections,
    String summary,
    String coverImage,
    String videoUrl,
    Integer readTimeMinutes,
    boolean isPremium,
    boolean locked
) {

    /**
     * A structured section within a lesson.
     *
     * @param heading             section heading
     * @param body                section body text
     * @param tip                 optional tip callout
     * @param keyRule             optional key rule summary
     * @param relatedRoadSignCode optional related road sign reference
     * @param sortOrder           display sort order
     */
    public record LessonSectionDto(
        String heading,
        String body,
        String tip,
        String keyRule,
        String relatedRoadSignCode,
        Integer sortOrder
    ) { }
}
