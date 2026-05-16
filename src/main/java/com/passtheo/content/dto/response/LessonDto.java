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
     * @param heading   section heading
     * @param body      section body text
     * @param tip       optional tip callout
     * @param warning   optional warning callout (exam traps, common mistakes)
     * @param keyRule   optional key rule summary
     * @param image     optional image with caption + alt text
     * @param roadSigns zero or more road-sign references (code + name). Never
     *                  {@code null} — empty list when no signs are linked.
     */
    public record LessonSectionDto(
        String heading,
        String body,
        String tip,
        String warning,
        String keyRule,
        SectionImageDto image,
        List<RoadSignRefDto> roadSigns
    ) { }

    /**
     * Image attached to a lesson section.
     *
     * @param url     absolute or relative URL of the underlying file
     * @param caption optional localized caption rendered below the image
     * @param alt     optional localized alternative text for accessibility
     */
    public record SectionImageDto(
        String url,
        String caption,
        String alt
    ) { }

    /**
     * Road-sign reference attached to a lesson section. Carries the minimum
     * fields a client needs to render and link to the full sign.
     *
     * @param code stable road-sign code (e.g. "B1", "B6")
     * @param name localized road-sign display name
     */
    public record RoadSignRefDto(
        String code,
        String name
    ) { }
}
