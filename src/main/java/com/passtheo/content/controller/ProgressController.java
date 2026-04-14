package com.passtheo.content.controller;

import com.passtheo.content.domain.valueobject.ExamConfidence;
import com.passtheo.content.domain.valueobject.ReadinessScore;
import com.passtheo.content.domain.valueobject.XpResult;
import com.passtheo.content.dto.response.DomainProgressDto;
import com.passtheo.content.dto.response.MasteryStatsDto;
import com.passtheo.content.dto.response.ReadinessDto;
import com.passtheo.content.dto.response.ReadinessSnapshotDto;
import com.passtheo.content.dto.response.XpUpdateDto;
import com.passtheo.content.service.PracticeSessionService;
import com.passtheo.content.service.ProgressService;
import com.passtheo.content.service.ReadinessService;
import com.passtheo.content.service.XpService;
import com.passtheo.shared.core.dto.ApiResponse;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final PracticeSessionService practiceSessionService;
    private final XpService xpService;

    /**
     * Constructs the progress controller.
     *
     * @param readinessService       readiness score service
     * @param progressService        progress aggregation service
     * @param practiceSessionService practice session service (for flag/unflag)
     * @param xpService              XP and level service
     */
    public ProgressController(ReadinessService readinessService, ProgressService progressService,
                              PracticeSessionService practiceSessionService, XpService xpService) {
        this.readinessService = readinessService;
        this.progressService = progressService;
        this.practiceSessionService = practiceSessionService;
        this.xpService = xpService;
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
        ExamConfidence confidence = score.examConfidence();
        ExamConfidence.Breakdown cb = confidence.breakdown();
        ReadinessDto dto = new ReadinessDto(
                score.readinessScore(), score.coverageScore(), score.accuracyScore(),
                score.examScore(), score.readinessLabel().name(),
                score.questionsAttempted(), score.totalQuestions(),
                score.bestExamScore(), score.examPassScore(),
                score.domainStrengths().stream()
                        .map(ds -> new ReadinessDto.DomainStrengthDto(
                                ds.domainCode(), ds.domainName(),
                                ds.accuracyPercent(), ds.coveragePercent(), ds.strength()))
                        .toList(),
                confidence.score(), confidence.label().name(), confidence.recommendation().name(),
                new ReadinessDto.ConfidenceBreakdownDto(
                        cb.coveragePoints(), cb.accuracyPoints(), cb.examConsistencyPoints(),
                        cb.avgScorePoints(), cb.noWeakDomainsPoints(),
                        cb.coverageMet(), cb.accuracyMet(), cb.consecutivePasses(), cb.weakDomainCodes())
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
     * Gets the current XP and level for the authenticated user.
     *
     * @param userId      user ID from header
     * @param productCode the product code
     * @return current XP state
     */
    @GetMapping("/xp")
    public ResponseEntity<ApiResponse<XpUpdateDto>> getUserXp(
            @RequestHeader("X-Keycloak-User-ID") UUID userId,
            @RequestParam @Nonnull String productCode) {

        XpResult result = xpService.getXp(userId, productCode);
        XpUpdateDto dto = new XpUpdateDto(
                result.xpEarned(), result.totalXp(), result.currentLevel(),
                result.xpForNextLevel(), result.leveledUp());
        return ResponseEntity.ok(ApiResponse.success(dto, MDC.get("traceId")));
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

    /**
     * Flags a question for extra practice.
     *
     * @param userId           user ID from header
     * @param strapiQuestionId the Strapi question document ID
     * @return 204 No Content
     */
    @PostMapping("/questions/{strapiQuestionId}/flag")
    public ResponseEntity<Void> flagQuestion(
            @RequestHeader("X-Keycloak-User-ID") UUID userId,
            @PathVariable @Nonnull String strapiQuestionId) {

        practiceSessionService.flagQuestion(userId, strapiQuestionId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Unflags a question.
     *
     * @param userId           user ID from header
     * @param strapiQuestionId the Strapi question document ID
     * @return 204 No Content
     */
    @DeleteMapping("/questions/{strapiQuestionId}/flag")
    public ResponseEntity<Void> unflagQuestion(
            @RequestHeader("X-Keycloak-User-ID") UUID userId,
            @PathVariable @Nonnull String strapiQuestionId) {

        practiceSessionService.unflagQuestion(userId, strapiQuestionId);
        return ResponseEntity.noContent().build();
    }
}
