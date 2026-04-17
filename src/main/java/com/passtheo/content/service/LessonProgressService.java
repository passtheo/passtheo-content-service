package com.passtheo.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.passtheo.content.domain.entity.LessonProgress;
import com.passtheo.content.domain.valueobject.XpResult;
import com.passtheo.content.dto.response.EarnedAchievementDto;
import com.passtheo.content.dto.response.LessonCompleteResponse;
import com.passtheo.content.dto.response.LessonProgressDto;
import com.passtheo.content.dto.response.XpUpdateDto;
import com.passtheo.content.repository.LessonProgressRepository;
import com.passtheo.shared.core.context.TenantContext;
import com.passtheo.shared.events.config.KafkaTopic;
import com.passtheo.shared.events.content.LessonCompletedEvent;
import com.passtheo.shared.outbox.entity.OutboxEvent;
import com.passtheo.shared.outbox.entity.OutboxStatus;
import com.passtheo.shared.outbox.repository.OutboxEventRepository;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates lesson completion: persists progress, grants XP on first completion,
 * checks achievements, and publishes a {@link LessonCompletedEvent} via the outbox.
 *
 * <p>Idempotent: re-completing a lesson returns a response with {@code xpEarned=0},
 * does not grant XP again, and does not publish a second event. Uncompleting preserves
 * {@code startedAt}, {@code completedAt}, and {@code timeSpentSeconds} and does not
 * refund XP.
 */
@Service
public class LessonProgressService {

    private static final Logger LOG = LoggerFactory.getLogger(LessonProgressService.class);

    private final LessonProgressRepository lessonProgressRepository;
    private final XpService xpService;
    private final AchievementService achievementService;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Constructs the lesson progress service.
     *
     * @param lessonProgressRepository lesson progress repository
     * @param xpService                XP service
     * @param achievementService       achievement service
     * @param outboxEventRepository    outbox event repository
     * @param objectMapper             JSON object mapper (Spring-configured with JavaTimeModule)
     */
    public LessonProgressService(LessonProgressRepository lessonProgressRepository,
                                 XpService xpService,
                                 AchievementService achievementService,
                                 OutboxEventRepository outboxEventRepository,
                                 ObjectMapper objectMapper) {
        this.lessonProgressRepository = lessonProgressRepository;
        this.xpService = xpService;
        this.achievementService = achievementService;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Marks a lesson complete for a user. First-time completion grants XP, checks
     * achievements, and publishes a {@link LessonCompletedEvent}. Subsequent completions
     * are idempotent and return {@code xpEarned=0} with no event.
     *
     * @param userId           the user's Keycloak ID
     * @param lessonSlug       the lesson slug
     * @param productCode      the product code
     * @param topicCode        the topic code
     * @param timeSpentSeconds time spent reading the lesson
     * @return completion response including XP state and any newly-earned achievements
     */
    @Transactional
    public LessonCompleteResponse completeLesson(@Nonnull UUID userId,
                                                 @Nonnull String lessonSlug,
                                                 @Nonnull String productCode,
                                                 @Nonnull String topicCode,
                                                 int timeSpentSeconds) {
        Optional<LessonProgress> existing = lessonProgressRepository
                .findByKeycloakUserIdAndProductCodeAndLessonSlug(userId, productCode, lessonSlug);

        if (existing.isPresent() && existing.get().isCompleted()) {
            LessonProgress row = existing.get();
            XpResult current = xpService.getXp(userId, productCode);
            LOG.debug("Lesson already completed — returning idempotent response: user={}, slug={}",
                    userId, lessonSlug);
            return new LessonCompleteResponse(
                    lessonSlug,
                    true,
                    row.getCompletedAt(),
                    new XpUpdateDto(0, current.totalXp(), current.currentLevel(),
                            current.xpForNextLevel(), false),
                    List.of());
        }

        int safeTimeSpent = Math.max(0, timeSpentSeconds);
        Instant now = Instant.now();

        LessonProgress row = existing.orElseGet(() -> {
            LessonProgress created = new LessonProgress(userId, productCode, topicCode, lessonSlug);
            created.setTenantId(TenantContext.get());
            created.setStartedAt(now.minusSeconds(safeTimeSpent));
            return created;
        });
        row.setCompleted(true);
        row.setCompletedAt(now);
        row.setTimeSpentSeconds(row.getTimeSpentSeconds() + safeTimeSpent);
        lessonProgressRepository.save(row);

        List<EarnedAchievementDto> newAchievements = achievementService
                .checkAchievements(userId, productCode);
        int achievementXp = newAchievements.stream().mapToInt(EarnedAchievementDto::xpReward).sum();
        int totalGrant = XpService.XP_LESSON_COMPLETE + achievementXp;
        XpResult xpResult = xpService.grantXp(userId, productCode, totalGrant);

        publishLessonCompletedEvent(TenantContext.get(), userId, productCode, topicCode,
                lessonSlug, safeTimeSpent);

        LOG.info("Lesson completed: user={}, product={}, topic={}, slug={}, timeSpentSec={}, xp={}",
                userId, productCode, topicCode, lessonSlug, safeTimeSpent, totalGrant);

        return new LessonCompleteResponse(
                lessonSlug,
                true,
                row.getCompletedAt(),
                new XpUpdateDto(totalGrant, xpResult.totalXp(), xpResult.currentLevel(),
                        xpResult.xpForNextLevel(), xpResult.leveledUp()),
                newAchievements);
    }

    /**
     * Marks a lesson as not-completed. Preserves time spent, started_at, and completed_at.
     * Does NOT refund XP, does NOT remove earned achievements, and does NOT publish an event.
     *
     * @param userId      the user's Keycloak ID
     * @param lessonSlug  the lesson slug
     * @param productCode the product code
     */
    @Transactional
    public void uncompleteLesson(@Nonnull UUID userId,
                                 @Nonnull String lessonSlug,
                                 @Nonnull String productCode) {
        lessonProgressRepository
                .findByKeycloakUserIdAndProductCodeAndLessonSlug(userId, productCode, lessonSlug)
                .ifPresent(row -> {
                    row.setCompleted(false);
                    lessonProgressRepository.save(row);
                    LOG.info("Lesson uncompleted: user={}, product={}, slug={}",
                            userId, productCode, lessonSlug);
                });
    }

    /**
     * Returns the progress for every lesson the user has interacted with in a topic.
     * Lessons not yet touched are absent from the response (the UI defaults them to
     * {@code isCompleted=false}).
     *
     * @param userId      the user's Keycloak ID
     * @param productCode the product code
     * @param topicCode   the topic code
     * @return list of progress records for the topic
     */
    @Transactional(readOnly = true)
    public List<LessonProgressDto> getProgressForTopic(@Nonnull UUID userId,
                                                       @Nonnull String productCode,
                                                       @Nonnull String topicCode) {
        return lessonProgressRepository
                .findByKeycloakUserIdAndProductCodeAndTopicCode(userId, productCode, topicCode)
                .stream()
                .map(row -> new LessonProgressDto(
                        row.getLessonSlug(),
                        row.isCompleted(),
                        row.getCompletedAt(),
                        row.getTimeSpentSeconds()))
                .toList();
    }

    private void publishLessonCompletedEvent(UUID tenantId, UUID userId,
                                             String productCode, String topicCode,
                                             String lessonSlug, int timeSpentSeconds) {
        try {
            LessonCompletedEvent event = LessonCompletedEvent.create(
                    tenantId, userId, productCode, topicCode, lessonSlug, timeSpentSeconds);
            OutboxEvent outbox = new OutboxEvent();
            outbox.setTenantId(tenantId);
            outbox.setEventType(event.eventType());
            outbox.setTopic(KafkaTopic.CONTENT_EVENTS);
            outbox.setPayload(objectMapper.writeValueAsString(event));
            outbox.setStatus(OutboxStatus.PENDING);
            outbox.setPartitionKey(userId.toString());
            outboxEventRepository.save(outbox);
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize LessonCompletedEvent for user={}, slug={}",
                    userId, lessonSlug, e);
        }
    }
}
