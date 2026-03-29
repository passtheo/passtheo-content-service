package com.passtheo.content.controller;

import com.passtheo.content.domain.entity.DomainProgress;
import com.passtheo.content.domain.valueobject.AccessGrant;
import com.passtheo.content.dto.response.DomainSummaryDto;
import com.passtheo.content.integration.strapi.StrapiContentCache;
import com.passtheo.content.integration.strapi.dto.StrapiDomainDto;
import com.passtheo.content.repository.DomainProgressRepository;
import com.passtheo.content.service.EntitlementChecker;
import com.passtheo.shared.core.dto.ApiResponse;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
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
 * Returns the domain list with mastery stats for the practice domain grid.
 * Simpler alternative to the full content hierarchy endpoint — requires only productCode.
 */
@RestController
@RequestMapping("/api/practice")
public class PracticeDomainController {

    private static final Logger LOG = LoggerFactory.getLogger(PracticeDomainController.class);

    private final StrapiContentCache strapiContentCache;
    private final DomainProgressRepository domainProgressRepository;
    private final EntitlementChecker entitlementChecker;

    /**
     * Constructs the practice domain controller.
     *
     * @param strapiContentCache       Strapi content cache
     * @param domainProgressRepository domain progress repository
     * @param entitlementChecker       entitlement checker
     */
    public PracticeDomainController(StrapiContentCache strapiContentCache,
                                    DomainProgressRepository domainProgressRepository,
                                    EntitlementChecker entitlementChecker) {
        this.strapiContentCache = strapiContentCache;
        this.domainProgressRepository = domainProgressRepository;
        this.entitlementChecker = entitlementChecker;
    }

    /**
     * Returns all active domains for a product with the user's mastery breakdown.
     * Mastery breakdown: masteredCount / learningCount (seen but not mastered) / newCount (never seen).
     *
     * @param tenantId    tenant ID from header
     * @param userId      user ID from header
     * @param productCode the product code (e.g. "auto-b")
     * @param locale      content locale (default "nl")
     * @return list of domain summaries
     */
    @GetMapping("/domains")
    public ResponseEntity<ApiResponse<List<DomainSummaryDto>>> listPracticeDomains(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader(value = "X-Keycloak-User-ID", required = false) @Nullable UUID userId,
            @RequestParam @Nonnull String productCode,
            @RequestParam(defaultValue = "nl") String locale) {

        List<StrapiDomainDto> domains = strapiContentCache.getDomains(productCode, locale);
        AccessGrant access = userId != null
                ? entitlementChecker.getAccess(tenantId, userId)
                : AccessGrant.free();

        Map<String, DomainProgress> progressMap = userId != null
                ? domainProgressRepository.findByKeycloakUserIdAndProductCode(userId, productCode).stream()
                        .collect(Collectors.toMap(DomainProgress::getDomainCode, dp -> dp))
                : Map.of();

        List<DomainSummaryDto> result = domains.stream()
                .filter(StrapiDomainDto::isActive)
                .sorted(java.util.Comparator.comparingInt(StrapiDomainDto::sortOrder))
                .map(d -> {
                    int totalQuestions = d.questionCount() != null ? d.questionCount() : 0;
                    boolean isLocked = !access.isPaid() && !d.isFreePreview();
                    DomainProgress dp = progressMap.get(d.code());

                    int masteredCount = 0;
                    int learningCount = 0;
                    int newCount = totalQuestions;
                    double masteryPercent = 0.0;

                    if (dp != null) {
                        masteredCount = dp.getMasteredCount();
                        int attemptedCount = dp.getAttemptedCount();
                        learningCount = Math.max(0, attemptedCount - masteredCount);
                        newCount = Math.max(0, totalQuestions - attemptedCount);
                        masteryPercent = totalQuestions > 0
                                ? (double) masteredCount / totalQuestions * 100.0 : 0.0;
                    }

                    return new DomainSummaryDto(
                            d.code(), d.name(), d.icon(), d.color(),
                            totalQuestions, masteredCount, learningCount, newCount,
                            masteryPercent, isLocked);
                })
                .toList();

        LOG.debug("Listed {} practice domains for user={} product={}", result.size(), userId, productCode);
        return ResponseEntity.ok(ApiResponse.success(result, MDC.get("traceId")));
    }
}
