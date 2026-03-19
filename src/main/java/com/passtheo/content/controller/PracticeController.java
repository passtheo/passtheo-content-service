package com.passtheo.content.controller;

import com.passtheo.content.dto.request.StartSessionRequest;
import com.passtheo.content.dto.request.SubmitAnswerRequest;
import com.passtheo.content.dto.response.AnswerResultDto;
import com.passtheo.content.dto.response.SessionDto;
import com.passtheo.content.dto.response.SessionSummaryDto;
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

    /**
     * Constructs the practice controller.
     *
     * @param practiceSessionService practice session service
     * @param entitlementChecker     entitlement checker
     */
    public PracticeController(PracticeSessionService practiceSessionService,
                              EntitlementChecker entitlementChecker) {
        this.practiceSessionService = practiceSessionService;
        this.entitlementChecker = entitlementChecker;
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
     *
     * @param sessionId the session ID
     * @param tenantId  tenant ID from header
     * @param userId    user ID from header
     * @param request   answer request
     * @return answer result with feedback + next question
     */
    @PostMapping("/{sessionId}/answer")
    public ResponseEntity<ApiResponse<AnswerResultDto>> submitAnswer(
            @PathVariable @Nonnull UUID sessionId,
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-Keycloak-User-ID") UUID userId,
            @RequestBody @Valid @Nonnull SubmitAnswerRequest request,
            @RequestParam(defaultValue = "nl") String locale) {

        AnswerResultDto result = practiceSessionService.submitAnswer(userId, sessionId, request, locale);
        return ResponseEntity.ok(ApiResponse.success(result, MDC.get("traceId")));
    }

    /**
     * Gets current session state (for resuming after app close).
     *
     * @param sessionId the session ID
     * @param userId    user ID from header
     * @param locale    content locale
     * @return session with current question
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<SessionDto>> getSession(
            @PathVariable @Nonnull UUID sessionId,
            @RequestHeader("X-Keycloak-User-ID") UUID userId,
            @RequestParam(defaultValue = "nl") String locale) {

        SessionDto session = practiceSessionService.getSession(userId, sessionId, locale);
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
}
