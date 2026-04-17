package com.passtheo.content.service;

import com.passtheo.content.domain.entity.UserXp;
import com.passtheo.content.domain.valueobject.XpResult;
import com.passtheo.content.repository.UserXpRepository;
import com.passtheo.shared.core.context.TenantContext;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Grants experience points, tracks cumulative totals, and calculates levels.
 *
 * <p>Level formula: threshold for level N = N * (N - 1) * 50.
 * <ul>
 *   <li>Level 2 = 100 XP</li>
 *   <li>Level 3 = 300 XP</li>
 *   <li>Level 5 = 1000 XP</li>
 *   <li>Level 10 = 4500 XP</li>
 * </ul>
 */
@Service
public class XpService {

    private static final Logger LOG = LoggerFactory.getLogger(XpService.class);

    /** XP for a correct answer. */
    public static final int XP_CORRECT_ANSWER = 10;

    /** XP for a wrong answer (participation reward). */
    public static final int XP_WRONG_ANSWER = 2;

    /** Bonus XP for completing a practice session. */
    public static final int XP_PRACTICE_COMPLETE = 50;

    /** Bonus XP for passing a mock exam. */
    public static final int XP_EXAM_PASS = 200;

    /** Bonus XP for failing a mock exam (participation). */
    public static final int XP_EXAM_FAIL = 50;

    /** Additional bonus XP for a perfect exam score. */
    public static final int XP_PERFECT_EXAM = 500;

    /** XP for completing (reading) a lesson. Granted only on first completion. */
    public static final int XP_LESSON_COMPLETE = 20;

    private final UserXpRepository userXpRepository;

    /**
     * Constructs the XP service.
     *
     * @param userXpRepository user XP repository
     */
    public XpService(UserXpRepository userXpRepository) {
        this.userXpRepository = userXpRepository;
    }

    /**
     * Grants XP to a user for a product and recalculates their level.
     *
     * @param userId      the user's Keycloak ID
     * @param productCode the product code
     * @param xpAmount    the XP amount to grant (must be positive)
     * @return the XP result including new totals and level info
     */
    @Transactional
    public XpResult grantXp(@Nonnull UUID userId, @Nonnull String productCode, int xpAmount) {
        if (xpAmount <= 0) {
            return getXp(userId, productCode);
        }

        UserXp xp = userXpRepository.findByKeycloakUserIdAndProductCode(userId, productCode)
                .orElseGet(() -> createNew(userId, productCode));

        int previousLevel = xp.getCurrentLevel();
        xp.setTotalXp(xp.getTotalXp() + xpAmount);

        int newLevel = calculateLevel(xp.getTotalXp());
        xp.setCurrentLevel(newLevel);
        userXpRepository.save(xp);

        boolean leveledUp = newLevel > previousLevel;
        if (leveledUp) {
            LOG.info("Level up: user={}, product={}, level={}->{}, totalXp={}",
                    userId, productCode, previousLevel, newLevel, xp.getTotalXp());
        }

        return new XpResult(xpAmount, xp.getTotalXp(), newLevel,
                levelThreshold(newLevel + 1), leveledUp, previousLevel);
    }

    /**
     * Gets the current XP state without modifying it.
     *
     * @param userId      the user's Keycloak ID
     * @param productCode the product code
     * @return the current XP state
     */
    @Transactional(readOnly = true)
    public XpResult getXp(@Nonnull UUID userId, @Nonnull String productCode) {
        UserXp xp = userXpRepository.findByKeycloakUserIdAndProductCode(userId, productCode)
                .orElse(null);
        if (xp == null) {
            return new XpResult(0, 0, 1, levelThreshold(2), false, 1);
        }
        return new XpResult(0, xp.getTotalXp(), xp.getCurrentLevel(),
                levelThreshold(xp.getCurrentLevel() + 1), false, xp.getCurrentLevel());
    }

    /**
     * Calculates the level for a given total XP.
     * Threshold for level N = N * (N - 1) * 50.
     *
     * @param totalXp the total XP
     * @return the level (minimum 1)
     */
    public static int calculateLevel(int totalXp) {
        int level = 1;
        while (levelThreshold(level + 1) <= totalXp) {
            level++;
        }
        return level;
    }

    /**
     * Returns the XP threshold to reach a given level.
     *
     * @param level the level
     * @return the XP threshold
     */
    public static int levelThreshold(int level) {
        return level * (level - 1) * 50;
    }

    private UserXp createNew(UUID userId, String productCode) {
        UserXp xp = new UserXp(userId, productCode);
        xp.setTenantId(TenantContext.get());
        return xp;
    }
}
