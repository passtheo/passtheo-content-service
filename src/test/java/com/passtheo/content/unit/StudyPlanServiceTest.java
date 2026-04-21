package com.passtheo.content.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.passtheo.content.domain.enums.ConfidenceLabel;
import com.passtheo.content.domain.enums.MasteryLevel;
import com.passtheo.content.domain.enums.ReadinessLabel;
import com.passtheo.content.domain.enums.RecommendationKey;
import com.passtheo.content.domain.valueobject.ExamConfidence;
import com.passtheo.content.domain.valueobject.ReadinessScore;
import com.passtheo.content.domain.valueobject.ReadinessScore.DomainStrengthValue;
import com.passtheo.content.dto.request.GenerateStudyPlanRequest;
import com.passtheo.content.dto.response.StudyPlanDto;
import com.passtheo.content.integration.strapi.StrapiContentCache;
import com.passtheo.content.repository.QuestionProgressRepository;
import com.passtheo.content.repository.StudyPlanDayRepository;
import com.passtheo.content.repository.StudyPlanRepository;
import com.passtheo.content.domain.entity.StudyPlan;
import com.passtheo.content.domain.enums.PlanStatus;
import com.passtheo.content.service.ReadinessService;
import com.passtheo.content.service.StudyPlanService;
import com.passtheo.shared.core.client.UserServiceInternalClient;
import com.passtheo.shared.core.context.TenantContext;
import com.passtheo.shared.core.dto.InternalUserProfileDto;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for StudyPlanService — focus on the new {@code previewPlan}
 * method and its behavioural equivalence with {@code generatePlan}.
 */
@ExtendWith(MockitoExtension.class)
class StudyPlanServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String PRODUCT = "auto-b";
    private static final String LOCALE = "nl";

    @Mock private StudyPlanRepository planRepository;
    @Mock private StudyPlanDayRepository planDayRepository;
    @Mock private ReadinessService readinessService;
    @Mock private StrapiContentCache strapiContentCache;
    @Mock private UserServiceInternalClient userServiceClient;
    @Mock private QuestionProgressRepository progressRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MeterRegistry meterRegistry;

    private StudyPlanService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        meterRegistry = new SimpleMeterRegistry();
        service = new StudyPlanService(
                planRepository, planDayRepository, readinessService,
                strapiContentCache, objectMapper, userServiceClient, progressRepository,
                meterRegistry);

        ExamConfidence.Breakdown breakdown = new ExamConfidence.Breakdown(
                0, 0, 0, 0, 0, false, false, 0, List.of());
        ExamConfidence examConfidence = new ExamConfidence(
                0, ConfidenceLabel.NOT_READY, RecommendationKey.KEEP_PRACTICING, breakdown);
        ReadinessScore readiness = new ReadinessScore(
                0.0, 0.0, 0.0, 0.0, ReadinessLabel.NOT_READY, 0, 500, null, 44,
                List.of(
                        new DomainStrengthValue("verkeersborden", "Verkeersborden", 0.0, 0.0, "WEAK"),
                        new DomainStrengthValue("snelheid", "Snelheid", 0.0, 0.0, "MODERATE")),
                null, null, examConfidence);
        lenient().when(readinessService.calculate(eq(USER_ID), eq(PRODUCT), anyString()))
                .thenReturn(readiness);
        lenient().when(strapiContentCache.getQuestionCount(eq(PRODUCT), anyString())).thenReturn(500);
        lenient().when(progressRepository.countByKeycloakUserIdAndProductCodeAndMasteryLevel(
                eq(USER_ID), eq(PRODUCT), eq(MasteryLevel.MASTERED))).thenReturn(0L);
        lenient().when(userServiceClient.getProfile(eq(USER_ID), any())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void previewPlan_doesNotPersist() {
        GenerateStudyPlanRequest request = new GenerateStudyPlanRequest(
                PRODUCT, LocalDate.now(ZoneOffset.UTC).plusDays(60), null);

        StudyPlanDto preview = service.previewPlan(USER_ID, request, LOCALE);

        assertThat(preview).isNotNull();
        assertThat(preview.planId()).isNull();
        assertThat(preview.status()).isEqualTo("PREVIEW");
        verify(planRepository, never()).save(any());
        verify(planRepository, never()).saveAndFlush(any());
        verify(planDayRepository, never()).saveAll(any());
    }

    @Test
    void previewPlan_returnsSameShapeAsGenerate() {
        LocalDate examDate = LocalDate.now(ZoneOffset.UTC).plusDays(60);
        GenerateStudyPlanRequest request = new GenerateStudyPlanRequest(PRODUCT, examDate, null);

        when(planRepository.findByKeycloakUserIdAndProductCodeAndStatus(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(planRepository.save(any())).thenAnswer(i -> {
            var plan = (com.passtheo.content.domain.entity.StudyPlan) i.getArgument(0);
            plan.setId(UUID.randomUUID());
            return plan;
        });

        StudyPlanDto preview = service.previewPlan(USER_ID, request, LOCALE);
        StudyPlanDto generated = service.generatePlan(USER_ID, request, LOCALE);

        assertThat(preview.totalDays()).isEqualTo(generated.totalDays());
        assertThat(preview.dailyQuestionTarget()).isEqualTo(generated.dailyQuestionTarget());
        assertThat(preview.focusDomains()).containsExactlyElementsOf(generated.focusDomains());
        assertThat(preview.days()).hasSameSizeAs(generated.days());
    }

    @Test
    void previewPlan_usesMasteryCountForDailyGoal() {
        // 500 total questions, 50 mastered, 50 days until exam => ceil(450/50) = 9
        LocalDate examDate = LocalDate.now(ZoneOffset.UTC).plusDays(50);
        GenerateStudyPlanRequest request = new GenerateStudyPlanRequest(PRODUCT, examDate, null);
        when(progressRepository.countByKeycloakUserIdAndProductCodeAndMasteryLevel(
                eq(USER_ID), eq(PRODUCT), eq(MasteryLevel.MASTERED))).thenReturn(50L);

        StudyPlanDto preview = service.previewPlan(USER_ID, request, LOCALE);

        assertThat(preview.dailyQuestionTarget()).isEqualTo(9);
    }

    @Test
    void previewPlan_dailyTargetFloor() {
        // 50 remaining / 60 days => ceil = 1, clamped to floor of 5.
        LocalDate examDate = LocalDate.now(ZoneOffset.UTC).plusDays(60);
        when(strapiContentCache.getQuestionCount(eq(PRODUCT), anyString())).thenReturn(50);
        when(progressRepository.countByKeycloakUserIdAndProductCodeAndMasteryLevel(
                eq(USER_ID), eq(PRODUCT), eq(MasteryLevel.MASTERED))).thenReturn(0L);
        GenerateStudyPlanRequest request = new GenerateStudyPlanRequest(PRODUCT, examDate, null);

        StudyPlanDto preview = service.previewPlan(USER_ID, request, LOCALE);

        assertThat(preview.dailyQuestionTarget()).isEqualTo(5);
    }

    @Test
    void previewPlan_dailyTargetCeiling() {
        // 5000 remaining / 30 days => ceil = 167, clamped to ceiling of 50.
        LocalDate examDate = LocalDate.now(ZoneOffset.UTC).plusDays(30);
        when(strapiContentCache.getQuestionCount(eq(PRODUCT), anyString())).thenReturn(5000);
        when(progressRepository.countByKeycloakUserIdAndProductCodeAndMasteryLevel(
                eq(USER_ID), eq(PRODUCT), eq(MasteryLevel.MASTERED))).thenReturn(0L);
        GenerateStudyPlanRequest request = new GenerateStudyPlanRequest(PRODUCT, examDate, null);

        StudyPlanDto preview = service.previewPlan(USER_ID, request, LOCALE);

        assertThat(preview.dailyQuestionTarget()).isEqualTo(50);
    }

    @Test
    void previewPlan_totalDaysClampedToMaxPlanDays() {
        // 365 days until exam should clamp to MAX_PLAN_DAYS = 90.
        LocalDate examDate = LocalDate.now(ZoneOffset.UTC).plusDays(365);
        GenerateStudyPlanRequest request = new GenerateStudyPlanRequest(PRODUCT, examDate, null);

        StudyPlanDto preview = service.previewPlan(USER_ID, request, LOCALE);

        assertThat(preview.totalDays()).isEqualTo(90);
    }

    @Test
    void previewPlan_totalDaysClampedToMinPlanDays() {
        // 2 days until exam should clamp to MIN_PLAN_DAYS = 7.
        LocalDate examDate = LocalDate.now(ZoneOffset.UTC).plusDays(2);
        GenerateStudyPlanRequest request = new GenerateStudyPlanRequest(PRODUCT, examDate, null);

        StudyPlanDto preview = service.previewPlan(USER_ID, request, LOCALE);

        assertThat(preview.totalDays()).isEqualTo(7);
    }

    @Test
    void previewPlan_explicitDailyTargetOverridesAutoCalc() {
        LocalDate examDate = LocalDate.now(ZoneOffset.UTC).plusDays(60);
        GenerateStudyPlanRequest request = new GenerateStudyPlanRequest(PRODUCT, examDate, 42);

        StudyPlanDto preview = service.previewPlan(USER_ID, request, LOCALE);

        assertThat(preview.dailyQuestionTarget()).isEqualTo(42);
    }

    @Test
    void previewPlan_noExamDate_usesDefault30Days() {
        GenerateStudyPlanRequest request = new GenerateStudyPlanRequest(PRODUCT, null, null);

        StudyPlanDto preview = service.previewPlan(USER_ID, request, LOCALE);

        assertThat(preview.totalDays()).isEqualTo(30);
        assertThat(preview.dailyQuestionTarget()).isEqualTo(20);
    }

    // ─── getActivePlan: reconcile-on-read against user-service examDate ───

    private StudyPlan fakeActivePlan(LocalDate planExamDate) {
        StudyPlan plan = new StudyPlan();
        plan.setId(UUID.randomUUID());
        plan.setTenantId(TENANT_ID);
        plan.setKeycloakUserId(USER_ID);
        plan.setProductCode(PRODUCT);
        plan.setExamDate(planExamDate);
        plan.setTotalDays(30);
        plan.setStatus(PlanStatus.ACTIVE);
        plan.setDailyQuestionTarget(20);
        plan.setFocusDomains("[]");
        return plan;
    }

    private InternalUserProfileDto fakeProfile(LocalDate examDate) {
        return new InternalUserProfileDto(
                USER_ID, "test@example.com", true,
                java.time.Instant.parse("2026-01-01T00:00:00Z"),
                examDate, TENANT_ID);
    }

    @Test
    void getActivePlan_planExamDateMatchesProfile_returnsExistingPlanWithoutRegenerating() {
        LocalDate examDate = LocalDate.of(2026, 6, 1);
        StudyPlan existing = fakeActivePlan(examDate);
        when(planRepository.findByKeycloakUserIdAndProductCodeAndStatus(
                eq(USER_ID), eq(PRODUCT), eq(PlanStatus.ACTIVE)))
                .thenReturn(Optional.of(existing));
        when(userServiceClient.getProfile(eq(USER_ID), eq(TENANT_ID)))
                .thenReturn(Optional.of(fakeProfile(examDate)));
        when(planDayRepository.findByPlanIdOrderByDayNumberAsc(existing.getId()))
                .thenReturn(List.of());

        StudyPlanDto result = service.getActivePlan(USER_ID, PRODUCT);

        assertThat(result.planId()).isEqualTo(existing.getId());
        assertThat(result.examDate()).isEqualTo(examDate);
        verify(planRepository, never()).save(any());
        verify(planRepository, never()).saveAndFlush(any());
        assertThat(meterRegistry.find("passtheo_study_plan_drift_detected_total").counter()).isNull();
    }

    @Test
    void getActivePlan_planExamDateDiffersFromProfile_regeneratesAndReturnsRefreshedPlan() {
        LocalDate stalePlanDate = LocalDate.of(2030, 12, 31);
        LocalDate profileExamDate = LocalDate.of(2026, 6, 1);
        StudyPlan stale = fakeActivePlan(stalePlanDate);
        StudyPlan refreshed = fakeActivePlan(profileExamDate);

        // First call returns stale; after regeneration the second call returns refreshed.
        when(planRepository.findByKeycloakUserIdAndProductCodeAndStatus(
                eq(USER_ID), eq(PRODUCT), eq(PlanStatus.ACTIVE)))
                .thenReturn(Optional.of(stale))
                .thenReturn(Optional.of(stale))  // generatePlan's own pre-deactivate lookup
                .thenReturn(Optional.of(refreshed));  // post-regeneration reload
        when(userServiceClient.getProfile(eq(USER_ID), eq(TENANT_ID)))
                .thenReturn(Optional.of(fakeProfile(profileExamDate)));
        when(planRepository.save(any())).thenAnswer(i -> {
            StudyPlan p = i.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });
        when(planDayRepository.findByPlanIdOrderByDayNumberAsc(any()))
                .thenReturn(List.of());

        StudyPlanDto result = service.getActivePlan(USER_ID, PRODUCT);

        // generatePlan was called: it abandons the stale via saveAndFlush and saves the new plan.
        verify(planRepository, times(1)).saveAndFlush(any());
        verify(planRepository, times(1)).save(any());
        assertThat(result.examDate()).isEqualTo(profileExamDate);
    }

    @Test
    void getActivePlan_userProfileUnavailable_returnsStoredPlanUnchanged() {
        LocalDate planExamDate = LocalDate.of(2026, 6, 1);
        StudyPlan existing = fakeActivePlan(planExamDate);
        when(planRepository.findByKeycloakUserIdAndProductCodeAndStatus(
                eq(USER_ID), eq(PRODUCT), eq(PlanStatus.ACTIVE)))
                .thenReturn(Optional.of(existing));
        when(userServiceClient.getProfile(eq(USER_ID), eq(TENANT_ID)))
                .thenReturn(Optional.empty());
        when(planDayRepository.findByPlanIdOrderByDayNumberAsc(existing.getId()))
                .thenReturn(List.of());

        StudyPlanDto result = service.getActivePlan(USER_ID, PRODUCT);

        assertThat(result.planId()).isEqualTo(existing.getId());
        assertThat(result.examDate()).isEqualTo(planExamDate);
        verify(planRepository, never()).save(any());
        verify(planRepository, never()).saveAndFlush(any());
    }

    @Test
    void getActivePlan_profileExamDateNull_doesNotRegenerate() {
        // User cleared their exam date in user-service but the content-service still has an active plan
        // (e.g. UserExamDateCleared lost). Conservative behavior: do NOT regenerate to a null examDate
        // — return the stored plan as-is. The UserExamDateCleared consumer path is the authoritative
        // way to abandon a plan on exam-date-cleared.
        LocalDate planExamDate = LocalDate.of(2026, 6, 1);
        StudyPlan existing = fakeActivePlan(planExamDate);
        when(planRepository.findByKeycloakUserIdAndProductCodeAndStatus(
                eq(USER_ID), eq(PRODUCT), eq(PlanStatus.ACTIVE)))
                .thenReturn(Optional.of(existing));
        when(userServiceClient.getProfile(eq(USER_ID), eq(TENANT_ID)))
                .thenReturn(Optional.of(fakeProfile(null)));
        when(planDayRepository.findByPlanIdOrderByDayNumberAsc(existing.getId()))
                .thenReturn(List.of());

        StudyPlanDto result = service.getActivePlan(USER_ID, PRODUCT);

        assertThat(result.planId()).isEqualTo(existing.getId());
        verify(planRepository, never()).save(any());
        verify(planRepository, never()).saveAndFlush(any());
    }

    @Test
    void getActivePlan_regenerationThrows_returnsStoredPlanUnchanged() {
        // Drift is detected and the counter is incremented, but generatePlan throws
        // (e.g., Strapi unreachable). The orchestrator must fall back to the stored plan
        // rather than propagating the exception and returning 500 to the caller.
        LocalDate stalePlanDate = LocalDate.of(2030, 12, 31);
        LocalDate profileExamDate = LocalDate.of(2026, 6, 1);
        StudyPlan stale = fakeActivePlan(stalePlanDate);

        when(planRepository.findByKeycloakUserIdAndProductCodeAndStatus(
                eq(USER_ID), eq(PRODUCT), eq(PlanStatus.ACTIVE)))
                .thenReturn(Optional.of(stale));
        when(userServiceClient.getProfile(eq(USER_ID), eq(TENANT_ID)))
                .thenReturn(Optional.of(fakeProfile(profileExamDate)));
        // Force generatePlan to fail by making its first repository read throw.
        when(readinessService.calculate(eq(USER_ID), eq(PRODUCT), anyString()))
                .thenThrow(new RuntimeException("readiness service unavailable"));
        when(planDayRepository.findByPlanIdOrderByDayNumberAsc(stale.getId()))
                .thenReturn(List.of());

        StudyPlanDto result = service.getActivePlan(USER_ID, PRODUCT);

        assertThat(result.planId()).isEqualTo(stale.getId());
        assertThat(result.examDate()).isEqualTo(stalePlanDate);
        // Metric still fires — drift was observed even though repair failed.
        assertThat(meterRegistry.find("passtheo_study_plan_drift_detected_total")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void getActivePlan_driftDetected_incrementsDriftCounterExactlyOnce() {
        LocalDate stalePlanDate = LocalDate.of(2030, 12, 31);
        LocalDate profileExamDate = LocalDate.of(2026, 6, 1);
        StudyPlan stale = fakeActivePlan(stalePlanDate);
        StudyPlan refreshed = fakeActivePlan(profileExamDate);

        when(planRepository.findByKeycloakUserIdAndProductCodeAndStatus(
                eq(USER_ID), eq(PRODUCT), eq(PlanStatus.ACTIVE)))
                .thenReturn(Optional.of(stale))
                .thenReturn(Optional.of(stale))
                .thenReturn(Optional.of(refreshed));
        when(userServiceClient.getProfile(eq(USER_ID), eq(TENANT_ID)))
                .thenReturn(Optional.of(fakeProfile(profileExamDate)));
        when(planRepository.save(any())).thenAnswer(i -> {
            StudyPlan p = i.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });
        when(planDayRepository.findByPlanIdOrderByDayNumberAsc(any()))
                .thenReturn(List.of());

        service.getActivePlan(USER_ID, PRODUCT);

        assertThat(meterRegistry.find("passtheo_study_plan_drift_detected_total")
                .tag("productCode", PRODUCT)
                .tag("tenantId", TENANT_ID.toString())
                .counter().count()).isEqualTo(1.0);
    }
}
