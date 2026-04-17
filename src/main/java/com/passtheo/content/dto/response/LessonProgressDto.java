package com.passtheo.content.dto.response;

import java.time.Instant;

/**
 * Lesson progress snapshot for a single lesson.
 *
 * @param lessonSlug       the lesson slug
 * @param isCompleted      whether the user has completed the lesson
 * @param completedAt      when the lesson was completed (null if not yet)
 * @param timeSpentSeconds total time spent reading the lesson
 */
public record LessonProgressDto(
        String lessonSlug,
        boolean isCompleted,
        Instant completedAt,
        int timeSpentSeconds
) { }
