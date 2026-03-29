package com.passtheo.content.controller;

import com.passtheo.content.dto.request.StartExamRequest;
import com.passtheo.content.dto.request.SubmitExamRequest;
import com.passtheo.content.dto.response.ExamDto;
import com.passtheo.content.dto.response.ExamHistorySummaryDto;
import com.passtheo.content.dto.response.ExamResultDto;
import com.passtheo.content.service.EntitlementChecker;
import com.passtheo.content.service.MockExamService;
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
import java.util.List;
import java.util.UUID;

/**
 * Mock exam endpoints: start, submit, history.
 */
@RestController
@RequestMapping("/api/exams")
public class ExamController {

    private static final Logger LOG = LoggerFactory.getLogger(ExamController.class);

    private final MockExamService mockExamService;
    private final EntitlementChecker entitlementChecker;

    /**
     * Constructs the exam controller.
     *
     * @param mockExamService    mock exam service
     * @param entitlementChecker entitlement checker
     */
    public ExamController(MockExamService mockExamService,
                          EntitlementChecker entitlementChecker) {
        this.mockExamService = mockExamService;
        this.entitlementChecker = entitlementChecker;
    }

    /**
     * Starts a full mock exam.
     *
     * @param tenantId tenant ID from header
     * @param userId   user ID from header
     * @param request  start exam request
     * @return exam with all questions
     */
    @PostMapping("/mock/start")
    public ResponseEntity<?> startMockExam(
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-Keycloak-User-ID") UUID userId,
            @RequestBody @Valid @Nonnull StartExamRequest request) {

        // Block mock exams entirely for free users
        if (!entitlementChecker.canStartExam(tenantId, userId)) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.FORBIDDEN, "Upgrade to Pro to take mock exams.");
            problem.setType(URI.create("https://api.passtheo.nl/errors/premium-required"));
            problem.setTitle("Premium required");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
        }

        ExamDto exam = mockExamService.startExam(userId, request);
        return ResponseEntity.ok(ApiResponse.success(exam, MDC.get("traceId")));
    }

    /**
     * Submits all exam answers at once.
     *
     * @param examId  the exam attempt ID
     * @param tenantId tenant ID from header
     * @param userId  user ID from header
     * @param request submit exam request
     * @param locale  content locale
     * @return exam result
     */
    @PostMapping("/mock/{examId}/submit")
    public ResponseEntity<ApiResponse<ExamResultDto>> submitMockExam(
            @PathVariable @Nonnull UUID examId,
            @RequestHeader("X-Tenant-ID") UUID tenantId,
            @RequestHeader("X-Keycloak-User-ID") UUID userId,
            @RequestBody @Valid @Nonnull SubmitExamRequest request,
            @RequestParam(defaultValue = "nl") String locale) {

        ExamResultDto result = mockExamService.submitExam(userId, examId, request, locale);
        return ResponseEntity.ok(ApiResponse.success(result, MDC.get("traceId")));
    }

    /**
     * Gets exam history.
     *
     * @param userId      user ID from header
     * @param productCode product code
     * @param page        page number
     * @param size        page size
     * @return list of exam history
     */
    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<ExamHistorySummaryDto>>> getExamHistory(
            @RequestHeader("X-Keycloak-User-ID") UUID userId,
            @RequestParam @Nonnull String productCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        List<ExamHistorySummaryDto> history = mockExamService.getExamHistory(userId, productCode, page, size);
        return ResponseEntity.ok(ApiResponse.success(history, MDC.get("traceId")));
    }
}
