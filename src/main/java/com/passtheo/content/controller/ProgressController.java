package com.passtheo.content.controller;

import com.passtheo.content.domain.valueobject.ReadinessScore;
import com.passtheo.content.dto.response.DomainProgressDto;
import com.passtheo.content.dto.response.MasteryStatsDto;
import com.passtheo.content.dto.response.ReadinessDto;
import com.passtheo.content.dto.response.ReadinessSnapshotDto;
import com.passtheo.content.service.ProgressService;
import com.passtheo.content.service.ReadinessService;
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

import java.util.List;
import java.util.UUID;

/**
 * Progress endpoints: readiness score, domain progress, mastery stats.
 */
@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    private static final Logger LOG = LoggerFactory.getLogger(ProgressController.class);

    private final ReadinessService readinessService;
    private final ProgressService progressService;

    /**
     * Constructs the progress controller.
     *
     * @param readinessService readiness score service
     * @param progressService  progress aggregation service
     */
    public ProgressController(ReadinessService readinessService, ProgressService progressService) {
        this.readinessService = readinessService;
        this.progressService = progressService;
    }

    /**
     * Gets readiness score with domain breakdown.
     *
     * @param userId      user ID from header
     * @param productCode the product code
     * @param locale      content locale
     * @return readiness score
     */
    @GetMapping("/readiness")
    public ResponseEntity<ApiResponse<ReadinessDto>> getReadiness(
            @RequestHeader("X-Keycloak-User-ID") UUID userId,
            @RequestParam @Nonnull String productCode,
            @RequestParam(defaultValue = "nl") String locale) {

        ReadinessScore score = readinessService.calculate(userId, productCode, locale);
        ReadinessDto dto = new ReadinessDto(
                score.readinessScore(), score.coverageScore(), score.accuracyScore(),
                score.examScore(), score.readinessLabel().name(),
                score.questionsAttempted(), score.totalQuestions(),
                score.bestExamScore(), score.examPassScore(),
                score.domainStrengths().stream()
                        .map(ds -> new ReadinessDto.DomainStrengthDto(
                                ds.domainCode(), ds.domainName(),
                                ds.accuracyPercent(), ds.coveragePercent(), ds.strength()))
                        .toList()
        );
        return ResponseEntity.ok(ApiResponse.success(dto, MDC.get("traceId")));
    }

    /**
     * Gets daily readiness score trend.
     *
     * @param userId      user ID from header
     * @param productCode the product code
     * @param days        number of days to look back
     * @return list of daily snapshots
     */
    @GetMapping("/readiness/trend")
    public ResponseEntity<ApiResponse<List<ReadinessSnapshotDto>>> getReadinessTrend(
            @RequestHeader("X-Keycloak-User-ID") UUID userId,
            @RequestParam @Nonnull String productCode,
            @RequestParam(defaultValue = "30") int days) {

        List<ReadinessSnapshotDto> trend = progressService.getReadinessTrend(userId, productCode, days);
        return ResponseEntity.ok(ApiResponse.success(trend, MDC.get("traceId")));
    }

    /**
     * Gets domain progress breakdown.
     *
     * @param userId      user ID from header
     * @param productCode the product code
     * @param locale      content locale
     * @return list of domain progress
     */
    @GetMapping("/domains")
    public ResponseEntity<ApiResponse<List<DomainProgressDto>>> getDomainProgress(
            @RequestHeader("X-Keycloak-User-ID") UUID userId,
            @RequestParam @Nonnull String productCode,
            @RequestParam(defaultValue = "nl") String locale) {

        List<DomainProgressDto> progress = progressService.getDomainProgress(userId, productCode, locale);
        return ResponseEntity.ok(ApiResponse.success(progress, MDC.get("traceId")));
    }

    /**
     * Gets mastery level distribution (pie chart data).
     *
     * @param userId      user ID from header
     * @param productCode the product code
     * @param locale      content locale
     * @return mastery stats
     */
    @GetMapping("/mastery")
    public ResponseEntity<ApiResponse<MasteryStatsDto>> getMasteryStats(
            @RequestHeader("X-Keycloak-User-ID") UUID userId,
            @RequestParam @Nonnull String productCode,
            @RequestParam(defaultValue = "nl") String locale) {

        MasteryStatsDto stats = progressService.getMasteryStats(userId, productCode, locale);
        return ResponseEntity.ok(ApiResponse.success(stats, MDC.get("traceId")));
    }
}
