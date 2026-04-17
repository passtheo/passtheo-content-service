package com.passtheo.content.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * Response returned after marking a lesson complete.
 *
 * @param lessonSlug      the lesson slug
 * @param isCompleted     whether the lesson is now completed
 * @param completedAt     when the lesson was completed (null if somehow still incomplete)
 * @param xpUpdate        XP state after this operation (xpEarned is 0 for re-completions)
 * @param newAchievements achievements newly earned from this completion
 */
public record LessonCompleteResponse(
        String lessonSlug,
        boolean isCompleted,
        Instant completedAt,
        XpUpdateDto xpUpdate,
        List<EarnedAchievementDto> newAchievements
) { }
