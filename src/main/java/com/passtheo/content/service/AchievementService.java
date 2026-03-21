package com.passtheo.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.passtheo.content.domain.entity.EarnedAchievement;
import com.passtheo.content.domain.entity.OutboxEvent;
import com.passtheo.content.domain.enums.DomainStrength;
import com.passtheo.content.domain.enums.OutboxStatus;
import com.passtheo.content.dto.response.EarnedAchievementDto;
import com.passtheo.content.integration.strapi.StrapiContentCache;
import com.passtheo.content.integration.strapi.dto.StrapiAchievementDefDto;
import com.passtheo.content.repository.DomainProgressRepository;
import com.passtheo.content.repository.EarnedAchievementRepository;
import com.passtheo.content.repository.ExamAttemptRepository;
import com.passtheo.content.repository.OutboxEventRepository;
import com.passtheo.content.repository.QuestionProgressRepository;
import com.passtheo.content.repository.StreakRepository;
import com.passtheo.shared.core.context.TenantContext;
import com.passtheo.shared.events.config.KafkaTopic;
import com.passtheo.shared.events.content.AchievementEarnedEvent;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Checks and awards achievements after every answer submission.
 * 23 achievement triggers defined in Strapi, checked against current user stats.
 */
@Service
public class AchievementService {

    private static final Logger LOG = LoggerFactory.getLogger(AchievementService.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final EarnedAchievementRepository achievementRepository;
    private final QuestionProgressRepository progressRepository;
    private final StreakRepository streakRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final DomainProgressRepository domainProgressRepository;
    private final StrapiContentCache strapiContentCache;
    private final OutboxEventRepository outboxEventRepository;

    /**
     * Constructs the achievement service.
     *
     * @param achievementRepository    earned achievement repository
     * @param progressRepository       question progress repository
     * @param streakRepository         streak repository
     * @param examAttemptRepository    exam attempt repository
     * @param domainProgressRepository domain progress repository
     * @param strapiContentCache       Strapi content cache
     * @param outboxEventRepository    outbox event repository
     */
    public AchievementService(EarnedAchievementRepository achievementRepository,
                              QuestionProgressRepository progressRepository,
                              StreakRepository streakRepository,
                              ExamAttemptRepository examAttemptRepository,
                              DomainProgressRepository domainProgressRepository,
                              StrapiContentCache strapiContentCache,
                              OutboxEventRepository outboxEventRepository) {
        this.achievementRepository = achievementRepository;
        this.progressRepository = progressRepository;
        this.streakRepository = streakRepository;
        this.examAttemptRepository = examAttemptRepository;
        this.domainProgressRepository = domainProgressRepository;
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

            if (currentValue >= def.triggerValue()) {
                EarnedAchievement earned = new EarnedAchievement(
                        userId, def.code(), Instant.now(), currentValue);
                earned.setTenantId(TenantContext.get());
                achievementRepository.save(earned);

                newlyEarned.add(new EarnedAchievementDto(
                        def.code(), def.name(), def.icon()));

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
     * Gets the current value for a trigger type.
     *
     * @param triggerType the trigger type string
     * @param userId      the user's Keycloak ID
     * @param productCode the product code
     * @return the current value for comparison against triggerValue
     */
    private int getCurrentValue(String triggerType, UUID userId, String productCode) {
        return switch (triggerType) {
            case "questions_answered" -> progressRepository.countAttempted(userId, productCode);
            case "correct_streak" -> progressRepository.maxConsecutiveCorrect(userId, productCode);
            case "study_days_streak" -> streakRepository.findByKeycloakUserIdAndProductCode(userId, productCode)
                    .map(s -> s.getCurrentStreak())
                    .orElse(0);
            case "exams_passed" -> (int) examAttemptRepository
                    .countByKeycloakUserIdAndProductCodeAndPassedTrue(userId, productCode);
            case "perfect_exam" -> (int) examAttemptRepository.countPerfect(userId, productCode);
            case "domain_mastered" -> (int) domainProgressRepository
                    .countByKeycloakUserIdAndProductCodeAndStrength(userId, productCode, DomainStrength.MASTERED);
            case "readiness_score" -> 0; // calculated separately to avoid circular dependency
            case "fast_correct" -> 0; // tracked inline during answer processing
            default -> {
                LOG.warn("Unknown achievement trigger type: {}", triggerType);
                yield 0;
            }
        };
    }
}
