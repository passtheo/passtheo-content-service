package com.passtheo.content.controller;

import com.passtheo.content.domain.valueobject.AccessGrant;
import com.passtheo.content.dto.response.DomainSummaryDto;
import com.passtheo.content.service.EntitlementChecker;
import com.passtheo.content.service.PracticeSessionService;
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
import java.util.UUID;

/**
 * Returns the domain list with mastery stats for the practice domain grid.
 * Simpler alternative to the full content hierarchy endpoint — requires only productCode.
 */
@RestController
@RequestMapping("/api/practice")
public class PracticeDomainController {

    private static final Logger LOG = LoggerFactory.getLogger(PracticeDomainController.class);

    private final PracticeSessionService practiceSessionService;
    private final EntitlementChecker entitlementChecker;

    /**
     * Constructs the practice domain controller.
     *
     * @param practiceSessionService practice session service
     * @param entitlementChecker     entitlement checker
     */
    public PracticeDomainController(PracticeSessionService practiceSessionService,
                                    EntitlementChecker entitlementChecker) {
        this.practiceSessionService = practiceSessionService;
        this.entitlementChecker = entitlementChecker;
    }

    /**
     * Returns all active domains for a product with the user's mastery breakdown.
     * When {@code X-Keycloak-User-ID} is absent (direct service call), mastery
     * stats default to zero and access defaults to free tier.
     *
     * @param tenantId    tenant ID from header
     * @param userId      user ID from header (optional — not present when bypassing gateway)
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

        AccessGrant access = userId != null
                ? entitlementChecker.getAccess(tenantId, userId)
                : AccessGrant.free();

        List<DomainSummaryDto> result =
                practiceSessionService.listPracticeDomains(userId, productCode, locale, access);

        LOG.debug("Listed {} practice domains for user={} product={}", result.size(), userId, productCode);
        return ResponseEntity.ok(ApiResponse.success(result, MDC.get("traceId")));
    }
}
