package com.passtheo.content.controller;

import com.passtheo.content.dto.request.CompleteLessonRequest;
import com.passtheo.content.dto.response.LessonCompleteResponse;
import com.passtheo.content.dto.response.LessonProgressDto;
import com.passtheo.content.service.LessonProgressService;
import com.passtheo.shared.core.dto.ApiResponse;
import jakarta.annotation.Nonnull;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Lesson reading progress endpoints: mark complete, mark unread, and fetch
 * progress for every lesson in a topic.
 *
 * <p>The listing endpoints (GET lessons by topic) live in {@link ContentController}
 * because they return content — these endpoints here deal only with per-user progress.
 */
@RestController
@RequestMapping("/api/content/lessons")
public class LessonController {

    private final LessonProgressService lessonProgressService;

    /**
     * Constructs the lesson controller.
     *
     * @param lessonProgressService the lesson progress service
     */
    public LessonController(LessonProgressService lessonProgressService) {
        this.lessonProgressService = lessonProgressService;
    }

    /**
     * Marks a lesson complete. First-time completion grants +20 XP, checks achievements,
     * and publishes a LessonCompletedEvent. Idempotent — re-completing grants 0 XP.
     *
     * @param lessonSlug the lesson slug
     * @param request    the complete request (productCode, topicCode, timeSpentSeconds)
     * @param userId     the user's Keycloak ID
     * @return the completion response
     */
    @PostMapping("/{lessonSlug}/complete")
    public ResponseEntity<ApiResponse<LessonCompleteResponse>> complete(
            @PathVariable @Nonnull String lessonSlug,
            @RequestBody @Valid @Nonnull CompleteLessonRequest request,
            @RequestHeader("X-Keycloak-User-ID") UUID userId) {
        LessonCompleteResponse response = lessonProgressService.completeLesson(
                userId, lessonSlug, request.productCode(), request.topicCode(), request.timeSpentSeconds());
        return ResponseEntity.ok(ApiResponse.success(response, MDC.get("traceId")));
    }

    /**
     * Marks a lesson not-completed. Does not refund XP or remove achievements.
     *
     * @param lessonSlug  the lesson slug
     * @param productCode the product code
     * @param userId      the user's Keycloak ID
     * @return 204 No Content
     */
    @DeleteMapping("/{lessonSlug}/complete")
    public ResponseEntity<Void> uncomplete(
            @PathVariable @Nonnull String lessonSlug,
            @RequestParam @Nonnull String productCode,
            @RequestHeader("X-Keycloak-User-ID") UUID userId) {
        lessonProgressService.uncompleteLesson(userId, lessonSlug, productCode);
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns progress records for every lesson the user has interacted with in the topic.
     *
     * @param productCode the product code
     * @param topicCode   the topic code
     * @param userId      the user's Keycloak ID
     * @return progress list (empty if user has never completed a lesson in this topic)
     */
    @GetMapping("/progress")
    public ResponseEntity<ApiResponse<List<LessonProgressDto>>> getProgress(
            @RequestParam @Nonnull String productCode,
            @RequestParam @Nonnull String topicCode,
            @RequestHeader("X-Keycloak-User-ID") UUID userId) {
        List<LessonProgressDto> progress = lessonProgressService
                .getProgressForTopic(userId, productCode, topicCode);
        return ResponseEntity.ok(ApiResponse.success(progress, MDC.get("traceId")));
    }
}
