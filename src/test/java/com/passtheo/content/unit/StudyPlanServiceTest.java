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
import com.passtheo.content.service.ReadinessService;
import com.passtheo.content.service.StudyPlanService;
import com.passtheo.shared.core.client.UserServiceInternalClient;
import com.passtheo.shared.core.context.TenantContext;
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

    private StudyPlanService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        service = new StudyPlanService(
                planRepository, planDayRepository, readinessService,
                strapiContentCache, objectMapper, userServiceClient, progressRepository);

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
}
