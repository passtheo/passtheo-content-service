package com.passtheo.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.passtheo.content.domain.entity.EarnedAchievement;
import com.passtheo.content.domain.entity.ExamAttempt;
import com.passtheo.content.domain.entity.ReadinessSnapshot;
import com.passtheo.shared.outbox.entity.OutboxEvent;
import com.passtheo.content.domain.enums.DomainStrength;
import com.passtheo.shared.outbox.entity.OutboxStatus;
import com.passtheo.content.dto.response.EarnedAchievementDto;
import com.passtheo.content.integration.strapi.StrapiContentCache;
import com.passtheo.content.integration.strapi.dto.StrapiAchievementDefDto;
import com.passtheo.content.repository.EarnedAchievementRepository;
import com.passtheo.content.repository.ExamAttemptRepository;
import com.passtheo.content.repository.ReadinessSnapshotRepository;
import com.passtheo.content.repository.SessionAnswerRepository;
import com.passtheo.content.repository.StudySessionRepository;
import com.passtheo.shared.outbox.repository.OutboxEventRepository;
import com.passtheo.content.repository.LessonProgressRepository;
import com.passtheo.content.repository.QuestionProgressRepository;
import com.passtheo.content.repository.StreakRepository;
import com.passtheo.shared.core.context.TenantContext;
import com.passtheo.shared.events.config.KafkaTopic;
import com.passtheo.shared.events.content.AchievementEarnedEvent;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Checks and awards achievements after every answer submission.
 * Achievement definitions are fetched from Strapi with trigger types and thresholds.
 *
 * <p>TODO: Performance — currently checks all achievements on every call. Consider
 * partitioning by trigger category so only relevant checks run per event type.
 */
@Service
public class AchievementService {

    private static final Logger LOG = LoggerFactory.getLogger(AchievementService.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Default locale used when computing domain mastery (content-agnostic — counts don't differ by locale). */
    private static final String DEFAULT_LOCALE = "nl";

    /** Number of recent exams to fetch for consecutive pass calculation. */
    private static final int CONSECUTIVE_PASS_WINDOW = 20;

    private final EarnedAchievementRepository achievementRepository;
    private final QuestionProgressRepository progressRepository;
    private final StreakRepository streakRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final StudySessionRepository sessionRepository;
    private final SessionAnswerRepository answerRepository;
    private final ReadinessSnapshotRepository readinessSnapshotRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final StrapiContentCache strapiContentCache;
    private final OutboxEventRepository outboxEventRepository;

    /**
     * Constructs the achievement service.
     *
     * @param achievementRepository       earned achievement repository
     * @param progressRepository          question progress repository
     * @param streakRepository            streak repository
     * @param examAttemptRepository       exam attempt repository
     * @param sessionRepository           study session repository
     * @param answerRepository            session answer repository
     * @param readinessSnapshotRepository readiness snapshot repository
     * @param lessonProgressRepository    lesson progress repository
     * @param strapiContentCache          Strapi content cache
     * @param outboxEventRepository       outbox event repository
     */
    public AchievementService(EarnedAchievementRepository achievementRepository,
                              QuestionProgressRepository progressRepository,
                              StreakRepository streakRepository,
                              ExamAttemptRepository examAttemptRepository,
                              StudySessionRepository sessionRepository,
                              SessionAnswerRepository answerRepository,
                              ReadinessSnapshotRepository readinessSnapshotRepository,
                              LessonProgressRepository lessonProgressRepository,
                              StrapiContentCache strapiContentCache,
                              OutboxEventRepository outboxEventRepository) {
        this.achievementRepository = achievementRepository;
        this.progressRepository = progressRepository;
        this.streakRepository = streakRepository;
        this.examAttemptRepository = examAttemptRepository;
        this.sessionRepository = sessionRepository;
        this.answerRepository = answerRepository;
        this.readinessSnapshotRepository = readinessSnapshotRepository;
        this.lessonProgressRepository = lessonProgressRepository;
        this.strapiContentCache = strapiContentCache;
        this.outboxEventRepository = outboxEventRepository;
    }

    /**
     * Checks all achievement triggers and awards newly earned ones.
     *
     * @param userId      the user's Keycloak ID
     * @param productCode the product code
     * @return list of newly earned achievements
     */
    @Transactional
    public List<EarnedAchievementDto> checkAchievements(@Nonnull UUID userId, @Nonnull String productCode) {
        Set<String> alreadyEarned = achievementRepository.findEarnedCodes(userId);
        List<StrapiAchievementDefDto> defs = strapiContentCache.getAchievements(productCode);
        List<EarnedAchievementDto> newlyEarned = new ArrayList<>();

        for (StrapiAchievementDefDto def : defs) {
            if (alreadyEarned.contains(def.code())) {
                continue;
            }

            int currentValue = getCurrentValue(def.triggerType(), userId, productCode);

            if (isThresholdMet(def.triggerType(), currentValue, def.triggerValue())) {
                EarnedAchievement earned = new EarnedAchievement(
                        userId, def.code(), Instant.now(), currentValue);
                earned.setTenantId(TenantContext.get());
                achievementRepository.save(earned);

                newlyEarned.add(new EarnedAchievementDto(
                        def.code(), def.name(), def.icon(), def.xpReward()));

                publishAchievementEarnedEvent(TenantContext.get(), userId,
                        def.code(), def.name(), def.icon(), currentValue);

                LOG.info("Achievement earned: user={}, code={}, value={}",
                        userId, def.code(), currentValue);
            }
        }

        return List.copyOf(newlyEarned);
    }

    private void publishAchievementEarnedEvent(java.util.UUID tenantId, UUID userId,
                                               String code, String name, String icon, int triggerValue) {
        try {
            AchievementEarnedEvent event = AchievementEarnedEvent.create(
                    tenantId, userId, code, name, icon, triggerValue);
            OutboxEvent outbox = new OutboxEvent();
            outbox.setTenantId(tenantId);
            outbox.setEventType(event.eventType());
            outbox.setTopic(KafkaTopic.CONTENT_EVENTS);
            outbox.setPayload(OBJECT_MAPPER.writeValueAsString(event));
            outbox.setStatus(OutboxStatus.PENDING);
            outbox.setPartitionKey(userId.toString());
            outboxEventRepository.save(outbox);
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize AchievementEarnedEvent for user={}", userId, e);
        }
    }

    /**
     * Gets the current value for a trigger type. Public so the gallery endpoint
     * can show live progress for unearned achievements.
     *
     * @param triggerType the trigger type string (UPPERCASE Strapi enum)
     * @param userId      the user's Keycloak ID
     * @param productCode the product code
     * @return the current value for comparison against triggerValue
     */
    public int getCurrentValue(@Nonnull String triggerType, @Nonnull UUID userId,
                               @Nonnull String productCode) {
        return switch (triggerType) {
            case "QUESTIONS_ANSWERED" -> progressRepository.countAttempted(userId, productCode);

            case "PERFECT_SESSION", "PERFECT_SESSIONS" ->
                    (int) sessionRepository.countPerfectSessions(userId, productCode);

            case "EXAMS_COMPLETED" -> (int) examAttemptRepository.countCompletedExams(userId, productCode);

            case "EXAMS_PASSED" -> (int) examAttemptRepository
                    .countByKeycloakUserIdAndProductCodeAndPassedTrue(userId, productCode);

            case "CONSECUTIVE_PASSES" -> countConsecutivePasses(userId, productCode);

            case "EXAM_SCORE" -> {
                Integer best = examAttemptRepository.findBestScore(userId, productCode);
                yield best != null ? best : 0;
            }

            case "STREAK_DAYS" -> streakRepository.findByKeycloakUserIdAndProductCode(userId, productCode)
                    .map(s -> s.getCurrentStreak())
                    .orElse(0);

            case "DOMAIN_MASTERED" -> countMasteredDomains(userId, productCode);

            case "ALL_DOMAINS_MASTERED" -> {
                int mastered = countMasteredDomains(userId, productCode);
                int totalDomains = strapiContentCache.getDomains(productCode, DEFAULT_LOCALE).size();
                // Yields 1 (threshold met) if all domains are mastered, 0 otherwise
                yield (totalDomains > 0 && mastered >= totalDomains) ? 1 : 0;
            }

            case "READINESS_SCORE" -> readinessSnapshotRepository
                    .findTopByKeycloakUserIdAndProductCodeOrderBySnapshotDateDesc(userId, productCode)
                    .map(ReadinessSnapshot::getReadinessScore)
                    .map(score -> score.intValue())
                    .orElse(0);

            case "AVG_ANSWER_TIME_BELOW" -> {
                Double avgMs = answerRepository.averageTimeTakenMs(userId, productCode);
                // Returns average answer time in whole seconds. The isThresholdMet method
                // handles the inverted comparison (lower is better) for this trigger type.
                if (avgMs == null) {
                    yield 0;
                }
                yield (int) (avgMs / 1000.0);
            }

            case "LESSONS_COMPLETED" ->
                    (int) lessonProgressRepository
                            .countByKeycloakUserIdAndProductCodeAndCompletedTrue(userId, productCode);

            case "LESSONS_COMPLETED_ALL" -> {
                int completed = (int) lessonProgressRepository
                        .countByKeycloakUserIdAndProductCodeAndCompletedTrue(userId, productCode);
                int total = strapiContentCache.getLessonCountForProduct(productCode, DEFAULT_LOCALE);
                // Yields 1 (threshold met) when every active lesson in the product has been
                // completed at least once, 0 otherwise.
                yield (total > 0 && completed >= total) ? 1 : 0;
            }

            case "TOPIC_LESSONS_COMPLETED" -> {
                // Yields 1 when any single topic has been fully completed (all lessons read).
                Map<String, Long> perTopic = new HashMap<>();
                for (Object[] row : lessonProgressRepository.countCompletedByTopic(userId, productCode)) {
                    perTopic.put((String) row[0], (Long) row[1]);
                }
                int result = 0;
                for (Map.Entry<String, Long> entry : perTopic.entrySet()) {
                    int topicTotal = strapiContentCache.getLessons(entry.getKey(), DEFAULT_LOCALE).size();
                    if (topicTotal > 0 && entry.getValue() >= topicTotal) {
                        result = 1;
                        break;
                    }
                }
                yield result;
            }

            default -> {
                LOG.warn("Unknown achievement trigger type: {}", triggerType);
                yield 0;
            }
        };
    }

    /**
     * Checks whether the current value meets the trigger threshold.
     * Most triggers use {@code currentValue >= triggerValue}, but "below" triggers
     * (e.g. AVG_ANSWER_TIME_BELOW) use an inverted comparison where lower is better.
     *
     * @param triggerType  the trigger type
     * @param currentValue the current measured value
     * @param triggerValue the threshold from the achievement definition
     * @return true if the achievement condition is met
     */
    public static boolean isThresholdMet(@Nonnull String triggerType, int currentValue, int triggerValue) {
        if ("AVG_ANSWER_TIME_BELOW".equals(triggerType)) {
            // currentValue is average seconds, triggerValue is the ceiling.
            // Earned when average > 0 (has data) and average <= threshold.
            return currentValue > 0 && currentValue <= triggerValue;
        }
        return currentValue >= triggerValue;
    }

    /**
     * Counts domains where the user has achieved MASTERED strength.
     *
     * @param userId      the user's Keycloak ID
     * @param productCode the product code
     * @return number of mastered domains
     */
    private int countMasteredDomains(@Nonnull UUID userId, @Nonnull String productCode) {
        return (int) progressRepository.aggregateByDomain(userId, productCode).stream()
                .filter(agg -> {
                    int total = strapiContentCache.getQuestionCountByDomain(agg.getDomainCode(), DEFAULT_LOCALE);
                    double accuracy = agg.getTotalAttempts() > 0
                            ? (double) agg.getCorrectCount() / agg.getTotalAttempts() * 100.0 : 0.0;
                    long clampedAttempted = Math.min(agg.getAttemptedCount(), total);
                    double coverage = total > 0
                            ? (double) clampedAttempted / total * 100.0 : 0.0;
                    return ReadinessService.classifyDomainStrength(accuracy, coverage) == DomainStrength.MASTERED;
                })
                .count();
    }

    /**
     * Counts consecutive passed exams from the most recent backward.
     *
     * @param userId      the user's Keycloak ID
     * @param productCode the product code
     * @return number of consecutive passes from the most recent attempt
     */
    private int countConsecutivePasses(@Nonnull UUID userId, @Nonnull String productCode) {
        List<ExamAttempt> recentExams = examAttemptRepository
                .findByKeycloakUserIdAndProductCodeOrderByCompletedAtDesc(
                        userId, productCode, PageRequest.of(0, CONSECUTIVE_PASS_WINDOW))
                .getContent();

        int count = 0;
        for (ExamAttempt exam : recentExams) {
            if (exam.isPassed()) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }
}
