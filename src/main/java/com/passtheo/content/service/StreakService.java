package com.passtheo.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.passtheo.shared.outbox.entity.OutboxEvent;
import com.passtheo.content.domain.entity.Streak;
import com.passtheo.shared.outbox.entity.OutboxStatus;
import com.passtheo.content.domain.valueobject.StreakResult;
import com.passtheo.shared.outbox.repository.OutboxEventRepository;
import com.passtheo.content.repository.SessionAnswerRepository;
import com.passtheo.content.repository.StreakRepository;
import com.passtheo.shared.core.context.TenantContext;
import com.passtheo.shared.events.config.KafkaTopic;
import com.passtheo.shared.events.content.StreakUpdatedEvent;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Manages daily study streaks and freeze slots.
 * 1 question/day to maintain streak, reset at 00:00 UTC.
 * Freeze slots earned at 7-day (1), 14-day (+2), 30-day (+3).
 */
@Service
public class StreakService {

    private static final Logger LOG = LoggerFactory.getLogger(StreakService.class);

    private static final int FREEZE_MILESTONE_7 = 7;
    private static final int FREEZE_MILESTONE_14 = 14;
    private static final int FREEZE_MILESTONE_30 = 30;
    private static final int FREEZE_AWARD_7 = 1;
    private static final int FREEZE_AWARD_14 = 2;
    private static final int FREEZE_AWARD_30 = 3;

    private final StreakRepository streakRepository;
    private final SessionAnswerRepository sessionAnswerRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Constructs the streak service.
     *
     * @param streakRepository        the streak repository
     * @param sessionAnswerRepository the session answer repository
     * @param outboxEventRepository   the outbox event repository
     * @param objectMapper            the Jackson object mapper (with JSR-310 module)
     */
    public StreakService(StreakRepository streakRepository,
                        SessionAnswerRepository sessionAnswerRepository,
                        OutboxEventRepository outboxEventRepository,
                        ObjectMapper objectMapper) {
        this.streakRepository = streakRepository;
        this.sessionAnswerRepository = sessionAnswerRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Updates the streak after an answer submission.
     * Retries once on optimistic locking failure (multi-pod safety).
     *
     * @param userId      the user's Keycloak ID
     * @param productCode the product code
     * @return the streak result
     */
    @Transactional
    public StreakResult updateStreak(@Nonnull UUID userId, @Nonnull String productCode) {
        try {
            return doUpdateStreak(userId, productCode);
        } catch (ObjectOptimisticLockingFailureException ex) {
            LOG.warn("Optimistic locking conflict on streak update for user={}, retrying once", userId);
            return doUpdateStreak(userId, productCode);
        }
    }

    private StreakResult doUpdateStreak(UUID userId, String productCode) {
        Streak streak = streakRepository.findByKeycloakUserIdAndProductCode(userId, productCode)
                .orElseGet(() -> createNewStreak(userId, productCode));

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate lastStudy = streak.getLastStudyDate();
        boolean isNewDay = false;

        if (lastStudy == null || lastStudy.isBefore(today)) {
            isNewDay = true;

            if (lastStudy != null && lastStudy.equals(today.minusDays(1))) {
                // Consecutive day — extend streak
                streak.setCurrentStreak(streak.getCurrentStreak() + 1);
            } else if (lastStudy != null && lastStudy.equals(today.minusDays(2))
                    && streak.getFreezeSlotsAvailable() > 0) {
                // Missed 1 day but has freeze slot
                streak.setFreezeSlotsAvailable(streak.getFreezeSlotsAvailable() - 1);
                streak.setFreezeSlotsUsed(streak.getFreezeSlotsUsed() + 1);
                streak.setCurrentStreak(streak.getCurrentStreak() + 1);
            } else if (lastStudy != null && !lastStudy.equals(today)) {
                // Streak broken
                streak.setCurrentStreak(1);
            } else if (lastStudy == null) {
                // First study day ever
                streak.setCurrentStreak(1);
            }

            streak.setLastStudyDate(today);
            streak.setTotalStudyDays(streak.getTotalStudyDays() + 1);

            if (streak.getCurrentStreak() > streak.getLongestStreak()) {
                streak.setLongestStreak(streak.getCurrentStreak());
            }

            awardFreezeSlots(streak);
        }

        streak.setUpdatedAt(java.time.Instant.now());
        streak = streakRepository.save(streak);

        boolean studiedToday = today.equals(streak.getLastStudyDate());
        boolean streakAtRisk = studiedToday && streak.getCurrentStreak() > 0;

        LOG.debug("Streak updated: user={}, product={}, current={}, longest={}, isNewDay={}",
                userId, productCode, streak.getCurrentStreak(), streak.getLongestStreak(), isNewDay);

        boolean milestone = streak.getCurrentStreak() == 7
                || streak.getCurrentStreak() == 14
                || streak.getCurrentStreak() == 30
                || streak.getCurrentStreak() == 60;
        publishStreakEvent(streak.getTenantId(), userId, productCode,
                streak.getCurrentStreak(), streak.getLongestStreak(), milestone, streakAtRisk);

        return new StreakResult(
                streak.getCurrentStreak(),
                streak.getLongestStreak(),
                streak.getTotalStudyDays(),
                streak.getLastStudyDate(),
                streak.getFreezeSlotsAvailable(),
                streak.getFreezeSlotsUsed(),
                studiedToday,
                streakAtRisk,
                isNewDay
        );
    }

    /**
     * Gets the current streak status without updating.
     *
     * @param userId      the user's Keycloak ID
     * @param productCode the product code
     * @return the streak result
     */
    @Transactional(readOnly = true)
    public StreakResult getStreak(@Nonnull UUID userId, @Nonnull String productCode) {
        Streak streak = streakRepository.findByKeycloakUserIdAndProductCode(userId, productCode)
                .orElseGet(() -> new Streak(userId, productCode));

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        boolean studiedToday = today.equals(streak.getLastStudyDate());
        boolean streakAtRisk = studiedToday && streak.getCurrentStreak() > 0;

        return new StreakResult(
                streak.getCurrentStreak(),
                streak.getLongestStreak(),
                streak.getTotalStudyDays(),
                streak.getLastStudyDate(),
                streak.getFreezeSlotsAvailable(),
                streak.getFreezeSlotsUsed(),
                studiedToday,
                streakAtRisk,
                false
        );
    }

    /**
     * Computes which of the last 7 days the user studied (answered at least one question).
     * Index 0 = 6 days ago, index 6 = today.
     *
     * @param userId      the user's Keycloak ID
     * @param productCode the product code
     * @return list of 7 booleans
     */
    @Transactional(readOnly = true)
    public List<Boolean> computeLastSevenDays(@Nonnull UUID userId, @Nonnull String productCode) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate sixDaysAgo = today.minusDays(6);
        Instant startDate = sixDaysAgo.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endDate = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        Set<LocalDate> studyDates = Set.copyOf(
                sessionAnswerRepository.findStudyDatesBetween(userId, productCode, startDate, endDate));

        List<Boolean> result = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            result.add(studyDates.contains(sixDaysAgo.plusDays(i)));
        }
        return List.copyOf(result);
    }

    private void publishStreakEvent(UUID tenantId, UUID userId, String productCode,
                                   int currentStreak, int longestStreak, boolean milestone, boolean atRisk) {
        try {
            StreakUpdatedEvent event = StreakUpdatedEvent.create(
                    tenantId, userId, productCode, currentStreak, longestStreak, milestone, atRisk);
            OutboxEvent outbox = new OutboxEvent();
            outbox.setTenantId(tenantId);
            outbox.setEventType(event.eventType());
            outbox.setTopic(KafkaTopic.CONTENT_EVENTS);
            outbox.setPayload(objectMapper.writeValueAsString(event));
            outbox.setStatus(OutboxStatus.PENDING);
            outbox.setPartitionKey(userId.toString());
            outboxEventRepository.save(outbox);
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize StreakUpdatedEvent for user={}", userId, e);
        }
    }

    private Streak createNewStreak(UUID userId, String productCode) {
        Streak streak = new Streak(userId, productCode);
        streak.setTenantId(TenantContext.get());
        return streak;
    }

    private void awardFreezeSlots(Streak streak) {
        int current = streak.getCurrentStreak();
        int totalAwarded = streak.getFreezeSlotsAvailable() + streak.getFreezeSlotsUsed();

        if (current >= FREEZE_MILESTONE_30 && totalAwarded < FREEZE_AWARD_7 + FREEZE_AWARD_14 + FREEZE_AWARD_30) {
            int toAward = (FREEZE_AWARD_7 + FREEZE_AWARD_14 + FREEZE_AWARD_30) - totalAwarded;
            streak.setFreezeSlotsAvailable(streak.getFreezeSlotsAvailable() + toAward);
        } else if (current >= FREEZE_MILESTONE_14 && totalAwarded < FREEZE_AWARD_7 + FREEZE_AWARD_14) {
            int toAward = (FREEZE_AWARD_7 + FREEZE_AWARD_14) - totalAwarded;
            streak.setFreezeSlotsAvailable(streak.getFreezeSlotsAvailable() + toAward);
        } else if (current >= FREEZE_MILESTONE_7 && totalAwarded < FREEZE_AWARD_7) {
            streak.setFreezeSlotsAvailable(streak.getFreezeSlotsAvailable() + FREEZE_AWARD_7);
        }
    }
}
