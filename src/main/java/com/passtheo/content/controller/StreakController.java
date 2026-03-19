package com.passtheo.content.controller;

import com.passtheo.content.domain.valueobject.StreakResult;
import com.passtheo.content.dto.response.StreakDto;
import com.passtheo.content.service.StreakService;
import com.passtheo.shared.core.dto.ApiResponse;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Streak endpoint: get current streak status.
 */
@RestController
@RequestMapping("/api/streaks")
public class StreakController {

    private static final Logger LOG = LoggerFactory.getLogger(StreakController.class);

    private final StreakService streakService;

    /**
     * Constructs the streak controller.
     *
     * @param streakService streak service
     */
    public StreakController(StreakService streakService) {
        this.streakService = streakService;
    }

    /**
     * Gets current streak status.
     *
     * @param userId      user ID from header
     * @param productCode the product code
     * @return streak status
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<StreakDto>> getMyStreak(
            @RequestHeader("X-Keycloak-User-ID") UUID userId,
            @RequestParam @Nonnull String productCode) {

        StreakResult result = streakService.getStreak(userId, productCode);
        StreakDto dto = new StreakDto(
                result.currentStreak(), result.longestStreak(), result.totalStudyDays(),
                result.lastStudyDate(), result.freezeSlotsAvailable(), result.freezeSlotsUsed(),
                result.studiedToday(), result.streakAtRisk()
        );
        return ResponseEntity.ok(ApiResponse.success(dto, MDC.get("traceId")));
    }
}
