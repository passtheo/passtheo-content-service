package com.passtheo.content.service;

import com.passtheo.content.domain.entity.Streak;
import com.passtheo.content.domain.valueobject.StreakResult;
import com.passtheo.content.repository.StreakRepository;
import com.passtheo.shared.core.context.TenantContext;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
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

    /**
     * Constructs the streak service.
     *
     * @param streakRepository the streak repository
     */
    public StreakService(StreakRepository streakRepository) {
        this.streakRepository = streakRepository;
    }

    /**
     * Updates the streak after an answer submission.
     *
     * @param userId      the user's Keycloak ID
     * @param productCode the product code
     * @return the streak result
     */
    @Transactional
    public StreakResult updateStreak(@Nonnull UUID userId, @Nonnull String productCode) {
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
