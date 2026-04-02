package com.passtheo.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.passtheo.content.domain.entity.StudyPlan;
import com.passtheo.content.domain.entity.StudyPlanDay;
import com.passtheo.content.domain.enums.DomainStrength;
import com.passtheo.content.domain.enums.PlanDayStatus;
import com.passtheo.content.domain.enums.PlanStatus;
import com.passtheo.content.domain.valueobject.ReadinessScore;
import com.passtheo.content.domain.enums.MasteryLevel;
import com.passtheo.content.dto.request.GenerateStudyPlanRequest;
import com.passtheo.content.dto.response.StudyPlanDayDto;
import com.passtheo.content.dto.response.StudyPlanDto;
import com.passtheo.shared.core.client.UserServiceInternalClient;
import com.passtheo.content.integration.strapi.StrapiContentCache;
import com.passtheo.content.integration.strapi.dto.StrapiDomainDto;
import com.passtheo.content.repository.QuestionProgressRepository;
import com.passtheo.content.repository.StudyPlanDayRepository;
import com.passtheo.content.repository.StudyPlanRepository;
import com.passtheo.shared.core.context.TenantContext;
import com.passtheo.shared.core.exception.AppException;
import com.passtheo.shared.core.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Generates study plans based on weak domains and exam date.
 * Weak domains get 3x more days than strong ones. Last 3 days: mixed review + daily exams.
 */
@Service
public class StudyPlanService {

    private static final Logger LOG = LoggerFactory.getLogger(StudyPlanService.class);

    private static final int MIN_PLAN_DAYS = 7;
    private static final int MAX_PLAN_DAYS = 90;
    private static final int DEFAULT_PLAN_DAYS = 30;
    private static final int MIXED_REVIEW_DAYS = 3;
    private static final int EXAM_INTERVAL_DAYS = 7;

    private static final double WEIGHT_WEAK = 3.0;
    private static final double WEIGHT_MODERATE = 2.0;
    private static final double WEIGHT_STRONG = 1.0;
    private static final double WEIGHT_MASTERED = 0.5;

    private final StudyPlanRepository planRepository;
    private final StudyPlanDayRepository planDayRepository;
    private final ReadinessService readinessService;
    private final StrapiContentCache strapiContentCache;
    private final ObjectMapper objectMapper;
    private final UserServiceInternalClient userServiceClient;
    private final QuestionProgressRepository progressRepository;

    /**
     * Constructs the study plan service.
     *
     * @param planRepository     study plan repository
     * @param planDayRepository  study plan day repository
     * @param readinessService   readiness score service
     * @param strapiContentCache Strapi content cache
     * @param objectMapper       JSON serializer
     * @param userServiceClient  user-service client for exam date fallback
     * @param progressRepository question progress repository for mastered count
     */
    public StudyPlanService(StudyPlanRepository planRepository,
                            StudyPlanDayRepository planDayRepository,
                            ReadinessService readinessService,
                            StrapiContentCache strapiContentCache,
                            ObjectMapper objectMapper,
                            UserServiceInternalClient userServiceClient,
                            QuestionProgressRepository progressRepository) {
        this.planRepository = planRepository;
        this.planDayRepository = planDayRepository;
        this.readinessService = readinessService;
        this.strapiContentCache = strapiContentCache;
        this.objectMapper = objectMapper;
        this.userServiceClient = userServiceClient;
        this.progressRepository = progressRepository;
    }

    /**
     * Generates a new study plan, replacing any existing active plan.
     *
     * @param userId  the user's Keycloak ID
     * @param request the plan generation request
     * @param locale  content locale
     * @return the generated study plan
     */
    @Transactional
    public StudyPlanDto generatePlan(@Nonnull UUID userId, @Nonnull GenerateStudyPlanRequest request,
                                     @Nonnull String locale) {
        // Deactivate existing plan
        planRepository.findByKeycloakUserIdAndProductCodeAndStatus(
                        userId, request.productCode(), PlanStatus.ACTIVE)
                .ifPresent(existing -> {
                    existing.setStatus(PlanStatus.ABANDONED);
                    existing.setUpdatedAt(Instant.now());
                    planRepository.saveAndFlush(existing);
                });

        ReadinessScore readiness = readinessService.calculate(userId, request.productCode(), locale);

        LocalDate startDate = LocalDate.now(ZoneOffset.UTC);
        LocalDate resolvedExamDate = request.examDate();
        if (resolvedExamDate == null) {
            UUID tenantId = TenantContext.get();
            resolvedExamDate = userServiceClient.getProfile(userId, tenantId)
                    .map(p -> p.examDate())
                    .orElse(null);
            if (resolvedExamDate != null) {
                LOG.debug("Using exam date from user profile fallback: {}", resolvedExamDate);
            }
        }

        int totalDays;
        if (resolvedExamDate != null) {
            totalDays = (int) ChronoUnit.DAYS.between(startDate, resolvedExamDate);
            totalDays = Math.max(MIN_PLAN_DAYS, Math.min(totalDays, MAX_PLAN_DAYS));
        } else {
            totalDays = DEFAULT_PLAN_DAYS;
        }

        // Resolve dailyQuestionTarget: auto-calculate when not provided
        int resolvedDailyTarget;
        if (request.dailyQuestionTarget() != null) {
            resolvedDailyTarget = request.dailyQuestionTarget();
        } else if (resolvedExamDate != null) {
            long daysUntilExam = ChronoUnit.DAYS.between(startDate, resolvedExamDate);
            if (daysUntilExam > 0) {
                long totalQuestions = strapiContentCache.getQuestionCount(request.productCode(), locale);
                long mastered = progressRepository.countByKeycloakUserIdAndProductCodeAndMasteryLevel(
                        userId, request.productCode(), MasteryLevel.MASTERED);
                long remaining = Math.max(totalQuestions - mastered, 0);
                resolvedDailyTarget = (int) Math.min(50, Math.max(5,
                        (long) Math.ceil((double) remaining / daysUntilExam)));
            } else {
                resolvedDailyTarget = 20;
            }
        } else {
            resolvedDailyTarget = 20;
        }

        // Sort domains by weakness; fall back to all Strapi domains if no progress exists
        List<ReadinessScore.DomainStrengthValue> domains = new ArrayList<>(readiness.domainStrengths());
        if (domains.isEmpty()) {
            domains = strapiContentCache.getDomains(request.productCode(), locale).stream()
                    .map(d -> new ReadinessScore.DomainStrengthValue(
                            d.code(), d.name(), 0.0, 0.0, "WEAK"))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        }
        domains.sort(Comparator.comparingInt(d -> strengthOrdinal(d.strength())));

        Map<String, Integer> allocation = allocateDays(domains, totalDays);

        // Generate daily tasks
        List<StudyPlanDay> days = new ArrayList<>();
        int dayNumber = 1;
        LocalDate planDate = startDate;

        for (Map.Entry<String, Integer> entry : allocation.entrySet()) {
            for (int i = 0; i < entry.getValue() && dayNumber <= totalDays - MIXED_REVIEW_DAYS; i++) {
                StudyPlanDay day = new StudyPlanDay();
                day.setTenantId(TenantContext.get());
                day.setDayNumber(dayNumber);
                day.setPlanDate(planDate);
                day.setDomainCode(entry.getKey());
                day.setQuestionTarget(resolvedDailyTarget);
                day.setQuestionsCompleted(0);
                day.setIncludeExam(dayNumber % EXAM_INTERVAL_DAYS == 0);
                day.setStatus(PlanDayStatus.PENDING);
                days.add(day);
                dayNumber++;
                planDate = planDate.plusDays(1);
            }
        }

        // Last 3 days: mixed review + daily mock exams
        for (int i = 0; i < MIXED_REVIEW_DAYS && dayNumber <= totalDays; i++) {
            StudyPlanDay day = new StudyPlanDay();
            day.setTenantId(TenantContext.get());
            day.setDayNumber(dayNumber);
            day.setPlanDate(planDate);
            day.setDomainCode("ALL");
            day.setQuestionTarget(request.dailyQuestionTarget());
            day.setQuestionsCompleted(0);
            day.setIncludeExam(true);
            day.setStatus(PlanDayStatus.PENDING);
            days.add(day);
            dayNumber++;
            planDate = planDate.plusDays(1);
        }

        // Focus domains
        List<String> focusDomains = domains.stream()
                .filter(d -> "WEAK".equals(d.strength()) || "MODERATE".equals(d.strength()))
                .map(ReadinessScore.DomainStrengthValue::domainCode)
                .toList();

        // Save plan
        StudyPlan plan = new StudyPlan();
        plan.setTenantId(TenantContext.get());
        plan.setKeycloakUserId(userId);
        plan.setProductCode(request.productCode());
        plan.setExamDate(request.examDate());
        plan.setTotalDays(days.size());
        plan.setStatus(PlanStatus.ACTIVE);
        plan.setDailyQuestionTarget(resolvedDailyTarget);
        plan.setFocusDomains(serializeJson(focusDomains));
        plan.setUpdatedAt(Instant.now());
        plan = planRepository.save(plan);

        // Save plan days
        for (StudyPlanDay day : days) {
            day.setPlanId(plan.getId());
        }
        planDayRepository.saveAll(days);

        LOG.info("Study plan generated: id={}, user={}, product={}, days={}, focusDomains={}",
                plan.getId(), userId, request.productCode(), days.size(), focusDomains);

        return toPlanDto(plan, days, 1);
    }

    /**
     * Gets the active study plan for a user/product.
     *
     * @param userId      the user's Keycloak ID
     * @param productCode the product code
     * @return the active plan, or throws if none
     */
    @Transactional(readOnly = true)
    public StudyPlanDto getActivePlan(@Nonnull UUID userId, @Nonnull String productCode) {
        StudyPlan plan = planRepository.findByKeycloakUserIdAndProductCodeAndStatus(
                        userId, productCode, PlanStatus.ACTIVE)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, ErrorCode.PLAN_NOT_FOUND, "No active study plan found"));

        List<StudyPlanDay> days = planDayRepository.findByPlanIdOrderByDayNumberAsc(plan.getId());

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        int currentDay = days.stream()
                .filter(d -> !d.getPlanDate().isAfter(today))
                .mapToInt(StudyPlanDay::getDayNumber)
                .max().orElse(1);

        return toPlanDto(plan, days, currentDay);
    }

    /**
     * Gets today's study plan tasks.
     *
     * @param userId      the user's Keycloak ID
     * @param productCode the product code
     * @return today's plan day
     */
    @Transactional(readOnly = true)
    public StudyPlanDayDto getTodaysTasks(@Nonnull UUID userId, @Nonnull String productCode) {
        StudyPlan plan = planRepository.findByKeycloakUserIdAndProductCodeAndStatus(
                        userId, productCode, PlanStatus.ACTIVE)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, ErrorCode.PLAN_NOT_FOUND, "No active study plan found"));

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        StudyPlanDay todayDay = planDayRepository.findByPlanIdAndPlanDate(plan.getId(), today)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, ErrorCode.PLAN_NOT_FOUND, "No plan day for today"));

        String message = "ALL".equals(todayDay.getDomainCode())
                ? "Mixed review day — practice across all domains"
                : "Focus on " + todayDay.getDomainCode() + " today";

        return new StudyPlanDayDto(
                todayDay.getDayNumber(), todayDay.getPlanDate(),
                todayDay.getDomainCode(), todayDay.getDomainCode(),
                todayDay.getQuestionTarget(), todayDay.getQuestionsCompleted(),
                todayDay.isIncludeExam(), todayDay.getStatus().name(), message
        );
    }

    private Map<String, Integer> allocateDays(List<ReadinessScore.DomainStrengthValue> domains, int totalDays) {
        int available = totalDays - MIXED_REVIEW_DAYS;
        Map<String, Integer> allocation = new LinkedHashMap<>();

        double totalWeight = domains.stream()
                .mapToDouble(d -> getWeight(d.strength()))
                .sum();

        if (totalWeight == 0) {
            totalWeight = domains.size();
        }

        for (ReadinessScore.DomainStrengthValue d : domains) {
            double weight = getWeight(d.strength());
            int days = Math.max(1, (int) Math.round(available * weight / totalWeight));
            allocation.put(d.domainCode(), days);
        }

        return allocation;
    }

    private double getWeight(String strength) {
        return switch (strength) {
            case "WEAK" -> WEIGHT_WEAK;
            case "MODERATE" -> WEIGHT_MODERATE;
            case "STRONG" -> WEIGHT_STRONG;
            case "MASTERED" -> WEIGHT_MASTERED;
            default -> 1.0;
        };
    }

    private int strengthOrdinal(String strength) {
        return switch (strength) {
            case "WEAK" -> 0;
            case "MODERATE" -> 1;
            case "STRONG" -> 2;
            case "MASTERED" -> 3;
            default -> 4;
        };
    }

    @SuppressWarnings("unchecked")
    private StudyPlanDto toPlanDto(StudyPlan plan, List<StudyPlanDay> days, int currentDay) {
        List<String> focusDomains;
        try {
            focusDomains = plan.getFocusDomains() != null
                    ? objectMapper.readValue(plan.getFocusDomains(), List.class)
                    : List.of();
        } catch (JsonProcessingException e) {
            focusDomains = List.of();
        }

        List<StudyPlanDayDto> dayDtos = days.stream()
                .map(d -> new StudyPlanDayDto(
                        d.getDayNumber(), d.getPlanDate(),
                        d.getDomainCode(), d.getDomainCode(),
                        d.getQuestionTarget(), d.getQuestionsCompleted(),
                        d.isIncludeExam(), d.getStatus().name(), null))
                .toList();

        return new StudyPlanDto(
                plan.getId(), plan.getProductCode(), plan.getExamDate(),
                plan.getTotalDays(), currentDay, plan.getDailyQuestionTarget(),
                plan.getStatus().name(), focusDomains, dayDtos
        );
    }

    private String serializeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
