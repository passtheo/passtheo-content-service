package com.passtheo.content.controller;

import com.passtheo.content.domain.entity.EarnedAchievement;
import com.passtheo.content.dto.response.AchievementDto;
import com.passtheo.content.integration.strapi.StrapiContentCache;
import com.passtheo.content.integration.strapi.dto.StrapiAchievementDefDto;
import com.passtheo.content.repository.EarnedAchievementRepository;
import com.passtheo.content.service.AchievementService;
import com.passtheo.shared.core.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Achievement gallery endpoint: earned + locked achievements with live progress.
 */
@RestController
@RequestMapping("/api/achievements")
public class AchievementController {

    private static final Logger LOG = LoggerFactory.getLogger(AchievementController.class);

    private final EarnedAchievementRepository earnedAchievementRepository;
    private final StrapiContentCache strapiContentCache;
    private final AchievementService achievementService;

    /**
     * Constructs the achievement controller.
     *
     * @param earnedAchievementRepository earned achievement repository
     * @param strapiContentCache          Strapi content cache
     * @param achievementService          achievement service for live progress
     */
    public AchievementController(EarnedAchievementRepository earnedAchievementRepository,
                                 StrapiContentCache strapiContentCache,
                                 AchievementService achievementService) {
        this.earnedAchievementRepository = earnedAchievementRepository;
        this.strapiContentCache = strapiContentCache;
        this.achievementService = achievementService;
    }

    /**
     * Gets the achievement gallery (earned + locked with live progress).
     *
     * @param userId      user ID from header
     * @param productCode product code (optional)
     * @return list of all achievements with earn status and progress
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<List<AchievementDto>>> getMyAchievements(
            @RequestHeader("X-Keycloak-User-ID") UUID userId,
            @RequestParam(required = false) String productCode) {

        String resolvedProductCode = productCode != null ? productCode : "";
        List<StrapiAchievementDefDto> allDefs = strapiContentCache.getAchievements(resolvedProductCode);
        Map<String, EarnedAchievement> earnedMap = earnedAchievementRepository
                .findByKeycloakUserId(userId).stream()
                .collect(Collectors.toMap(EarnedAchievement::getAchievementCode, ea -> ea));

        List<AchievementDto> result = allDefs.stream().map(def -> {
            EarnedAchievement earned = earnedMap.get(def.code());
            boolean isEarned = earned != null;

            int currentProgress;
            if (isEarned && earned.getTriggerValue() != null) {
                currentProgress = earned.getTriggerValue();
            } else {
                currentProgress = achievementService.getCurrentValue(
                        def.triggerType(), userId, resolvedProductCode);
            }

            double progressPercent;
            if ("AVG_ANSWER_TIME_BELOW".equals(def.triggerType())) {
                // Inverted: lower is better. 100% when at or below threshold, 0% when no data.
                progressPercent = currentProgress > 0 && currentProgress <= def.triggerValue()
                        ? 100.0 : (currentProgress > 0 ? Math.max(0.0,
                        (1.0 - (double) (currentProgress - def.triggerValue()) / def.triggerValue()) * 100.0) : 0.0);
            } else {
                progressPercent = def.triggerValue() > 0
                        ? Math.min(100.0, (double) currentProgress / def.triggerValue() * 100.0) : 0.0;
            }

            return new AchievementDto(
                    def.code(), def.name(), def.description(),
                    isEarned ? def.icon() : def.lockedIcon(),
                    isEarned, isEarned ? earned.getEarnedAt() : null,
                    def.triggerType(), def.triggerValue(),
                    currentProgress, progressPercent, def.xpReward()
            );
        }).toList();

        return ResponseEntity.ok(ApiResponse.success(result, MDC.get("traceId")));
    }
}
