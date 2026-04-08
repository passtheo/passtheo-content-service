package com.passtheo.content.controller;

import com.passtheo.content.dto.request.StartSessionRequest;
import com.passtheo.content.dto.request.SubmitAnswerRequest;
import com.passtheo.content.dto.response.ActiveSessionDto;
import com.passtheo.content.dto.response.AnswerResultDto;
import com.passtheo.content.dto.response.SessionBreakdownDto;
import com.passtheo.content.dto.response.SessionDto;
import com.passtheo.content.dto.response.SessionSummaryDto;
import com.passtheo.content.integration.subscription.SubscriptionClient;
import com.passtheo.content.service.EntitlementChecker;
import com.passtheo.content.service.PracticeSessionService;
import com.passtheo.shared.core.dto.ApiResponse;
import jakarta.annotation.Nonnull;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * Practice session endpoints: start, answer, resume, complete.
 */
@RestController
@RequestMapping("/api/practice/sessions")
public class PracticeController {

    private static final Logger LOG = LoggerFactory.getLogger(PracticeController.class);

    private final PracticeSessionService practiceSessionService;
    private final EntitlementChecker entitlementChecker;
    private final SubscriptionClient subscriptionClient;

    /**
     * Constructs the practice controller.
     *
     * @param practiceSessionService practice session service
     * @param entitlementChecker     entitlement checker
     * @param subscriptionClient     subscription client for usage tracking
     */
    public PracticeController(PracticeSessionService practiceSessionService,
                              EntitlementChecker entitlementChecker,
                              SubscriptionClient subscriptionClient) {
        this.practiceSessionService = practiceSessionService;
        this.entitlementChecker = entitlementChecker;
        this.subscriptionClient = subscriptionClient;
    }

    /**
     * Gets the most recent in-progress session for the dashboard "Continue Practicing" card.
     *
     * @param userId      user ID from header
     * @param productCode the product code
     * @param locale      content locale
     * @return active session or null
     */
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<ActiveSessionDto>> getActiveSession(
            @RequestHeader("X-Keycloak-User-ID") UUID userId,
            @RequestParam @Nonnull String productCode,
            @RequestParam(defaultValue = "nl") String locale) {

        ActiveSessionDto dto = practiceSessionService.getActiveSession(userId, productCode, locale);
        return ResponseEntity.ok(ApiResponse.success(dto, MDC.get("traceId")));
    }

    /**
     * Starts a new practice session.
     *
     * @param tenantId tenant ID from header
     * @param userId   user ID from header
     * @param request  start session request
     * @return the session with first question
     */
    @PostMapping
    public ResponseEntity<?> startSession(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-Keycloak-User-ID") UUID userId,
            @RequestBody @Valid @Nonnull StartSessionRequest request) {

        String locale = request.locale() != null ? request.locale() : "nl";

        if (!entitlementChecker.canStartSession(tenantId, userId, request.domainCode(), locale)) {
            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
            problem.setTitle("Domain locked");
            problem.setDetail("This domain requires a paid subscription");
            problem.setType(URI.create("about:blank"));
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
        }

        SessionDto session = practiceSessionService.startSession(userId, request);
        return ResponseEntity.ok(ApiResponse.success(session, MDC.get("traceId")));
    }

    /**
     * Submits an answer for the current question.
     * Content locale is read from the session record — no locale parameter needed.
     *
     * @param sessionId the session ID
     * @param tenantId  tenant ID from header
     * @param userId    user ID from header
     * @param request   answer request
     * @return answer result with feedback + next question
     */
    @PostMapping("/{sessionId}/answer")
    public ResponseEntity<?> submitAnswer(
            @PathVariable @Nonnull UUID sessionId,
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-Keycloak-User-ID") UUID userId,
            @RequestBody @Valid @Nonnull SubmitAnswerRequest request) {

        if (!subscriptionClient.incrementQuestionUsage(tenantId, userId)) {
            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
            problem.setTitle("Daily question limit reached");
            problem.setDetail("You have reached your daily question limit. Upgrade to continue practicing.");
            problem.setType(URI.create("about:blank"));
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(problem);
        }

        AnswerResultDto result = practiceSessionService.submitAnswer(userId, sessionId, request);
        return ResponseEntity.ok(ApiResponse.success(result, MDC.get("traceId")));
    }

    /**
     * Gets current session state (for resuming after app close).
     * Content locale is read from the session record.
     *
     * @param sessionId the session ID
     * @param userId    user ID from header
     * @return session with current question
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<SessionDto>> getSession(
            @PathVariable @Nonnull UUID sessionId,
            @RequestHeader("X-Keycloak-User-ID") UUID userId) {

        SessionDto session = practiceSessionService.getSession(userId, sessionId);
        return ResponseEntity.ok(ApiResponse.success(session, MDC.get("traceId")));
    }

    /**
     * Marks session as completed and returns summary.
     *
     * @param sessionId the session ID
     * @param userId    user ID from header
     * @return session summary
     */
    @PostMapping("/{sessionId}/complete")
    public ResponseEntity<ApiResponse<SessionSummaryDto>> completeSession(
            @PathVariable @Nonnull UUID sessionId,
            @RequestHeader("X-Keycloak-User-ID") UUID userId) {

        SessionSummaryDto summary = practiceSessionService.completeSession(userId, sessionId);
        return ResponseEntity.ok(ApiResponse.success(summary, MDC.get("traceId")));
    }

    /**
     * Returns full session breakdown with per-question detail for review.
     *
     * @param sessionId the session ID
     * @param userId    user ID from header
     * @return session breakdown with all questions, answers, and explanations
     */
    @GetMapping("/{sessionId}/breakdown")
    public ResponseEntity<ApiResponse<SessionBreakdownDto>> getSessionBreakdown(
            @PathVariable @Nonnull UUID sessionId,
            @RequestHeader("X-Keycloak-User-ID") UUID userId) {

        SessionBreakdownDto breakdown = practiceSessionService.getSessionBreakdown(userId, sessionId);
        return ResponseEntity.ok(ApiResponse.success(breakdown, MDC.get("traceId")));
    }
}
