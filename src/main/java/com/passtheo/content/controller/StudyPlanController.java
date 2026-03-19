package com.passtheo.content.controller;

import com.passtheo.content.dto.request.GenerateStudyPlanRequest;
import com.passtheo.content.dto.response.StudyPlanDayDto;
import com.passtheo.content.dto.response.StudyPlanDto;
import com.passtheo.content.service.StudyPlanService;
import com.passtheo.shared.core.dto.ApiResponse;
import jakarta.annotation.Nonnull;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Study plan endpoints: generate, get active, get today's tasks.
 */
@RestController
@RequestMapping("/api/study-plan")
public class StudyPlanController {

    private static final Logger LOG = LoggerFactory.getLogger(StudyPlanController.class);

    private final StudyPlanService studyPlanService;

    /**
     * Constructs the study plan controller.
     *
     * @param studyPlanService study plan service
     */
    public StudyPlanController(StudyPlanService studyPlanService) {
        this.studyPlanService = studyPlanService;
    }

    /**
     * Generates a new study plan.
     *
     * @param userId  user ID from header
     * @param request the plan generation request
     * @param locale  content locale
     * @return the generated plan
     */
    @PostMapping
    public ResponseEntity<ApiResponse<StudyPlanDto>> generateStudyPlan(
            @RequestHeader("X-Keycloak-User-ID") UUID userId,
            @RequestBody @Valid @Nonnull GenerateStudyPlanRequest request,
            @RequestParam(defaultValue = "nl") String locale) {

        StudyPlanDto plan = studyPlanService.generatePlan(userId, request, locale);
        return ResponseEntity.ok(ApiResponse.success(plan, MDC.get("traceId")));
    }

    /**
     * Gets the active study plan.
     *
     * @param userId      user ID from header
     * @param productCode the product code
     * @return the active plan
     */
    @GetMapping
    public ResponseEntity<ApiResponse<StudyPlanDto>> getActiveStudyPlan(
            @RequestHeader("X-Keycloak-User-ID") UUID userId,
            @RequestParam @Nonnull String productCode) {

        StudyPlanDto plan = studyPlanService.getActivePlan(userId, productCode);
        return ResponseEntity.ok(ApiResponse.success(plan, MDC.get("traceId")));
    }

    /**
     * Gets today's study plan tasks.
     *
     * @param userId      user ID from header
     * @param productCode the product code
     * @return today's plan day
     */
    @GetMapping("/today")
    public ResponseEntity<ApiResponse<StudyPlanDayDto>> getTodaysTasks(
            @RequestHeader("X-Keycloak-User-ID") UUID userId,
            @RequestParam @Nonnull String productCode) {

        StudyPlanDayDto today = studyPlanService.getTodaysTasks(userId, productCode);
        return ResponseEntity.ok(ApiResponse.success(today, MDC.get("traceId")));
    }
}
