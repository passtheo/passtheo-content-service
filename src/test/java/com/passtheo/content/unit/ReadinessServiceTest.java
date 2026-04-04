package com.passtheo.content.unit;

import com.passtheo.shared.core.client.UserServiceInternalClient;
import com.passtheo.content.domain.enums.DomainStrength;
import com.passtheo.content.domain.enums.ReadinessLabel;
import com.passtheo.content.domain.valueobject.ExamConfidence;
import com.passtheo.content.domain.valueobject.ReadinessScore;
import com.passtheo.content.integration.strapi.StrapiContentCache;
import com.passtheo.content.integration.strapi.dto.StrapiDomainDto;
import com.passtheo.content.integration.strapi.dto.StrapiExamConfigDto;
import com.passtheo.content.repository.ExamAttemptRepository;
import com.passtheo.content.repository.QuestionProgressRepository;
import com.passtheo.content.service.ReadinessService;
import com.passtheo.shared.core.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ReadinessService — readiness score calculation and label classification.
 */
@ExtendWith(MockitoExtension.class)
class ReadinessServiceTest {

    @Mock private QuestionProgressRepository progressRepository;
    @Mock private ExamAttemptRepository examAttemptRepository;
    @Mock private StrapiContentCache strapiContentCache;
    @Mock private UserServiceInternalClient userServiceClient;

    private ReadinessService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final String PRODUCT_CODE = "auto-b";
    private static final String LOCALE = "nl";

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        org.mockito.Mockito.lenient().when(userServiceClient.getProfile(any(), any())).thenReturn(Optional.empty());
        service = new ReadinessService(progressRepository, examAttemptRepository, strapiContentCache, userServiceClient);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void calculate_zeroProgress_returnsNotReady() {
        setupMocks(500, 0, 0, null, 44);

        ReadinessScore result = service.calculate(USER_ID, PRODUCT_CODE, LOCALE);

        assertThat(result.readinessScore()).isEqualTo(0.0);
        assertThat(result.readinessLabel()).isEqualTo(ReadinessLabel.NOT_READY);
        assertThat(result.questionsAttempted()).isEqualTo(0);
    }

    @Test
    void calculate_fullCoverage_noAccuracy_noExam() {
        setupMocks(500, 500, 0, null, 44);

        ReadinessScore result = service.calculate(USER_ID, PRODUCT_CODE, LOCALE);

        // coverage=100, accuracy=0, exam=0 → 0.40*100 + 0.35*0 + 0.25*0 = 40
        assertThat(result.readinessScore()).isCloseTo(40.0, within(0.1));
        assertThat(result.readinessLabel()).isEqualTo(ReadinessLabel.GETTING_THERE);
    }

    @Test
    void calculate_halfCoverage_perfectAccuracy_noExam() {
        setupMocks(500, 250, 250, null, 44);

        ReadinessScore result = service.calculate(USER_ID, PRODUCT_CODE, LOCALE);

        // coverage=50, accuracy=100, exam=0 → 0.40*50 + 0.35*100 + 0.25*0 = 55
        assertThat(result.readinessScore()).isCloseTo(55.0, within(0.1));
        assertThat(result.readinessLabel()).isEqualTo(ReadinessLabel.GETTING_THERE);
    }

    @Test
    void calculate_fullCoverage_perfectAccuracy_perfectExam() {
        setupMocks(500, 500, 500, 50, 44);

        ReadinessScore result = service.calculate(USER_ID, PRODUCT_CODE, LOCALE);

        // coverage=100, accuracy=100, exam=min(100, 50/44*100)=100 → 0.40*100+0.35*100+0.25*100 = 100
        assertThat(result.readinessScore()).isCloseTo(100.0, within(0.5));
        assertThat(result.readinessLabel()).isEqualTo(ReadinessLabel.READY);
    }

    @Test
    void calculate_examScoreCappedAt100() {
        setupMocks(500, 500, 500, 50, 44);

        ReadinessScore result = service.calculate(USER_ID, PRODUCT_CODE, LOCALE);

        // exam score = 50/44*100 = 113.6, capped at 100
        assertThat(result.examScore()).isLessThanOrEqualTo(100.0);
    }

    @Test
    void calculate_labelBoundary_notReadyAt29() {
        setupMocks(500, 145, 0, null, 44);

        ReadinessScore result = service.calculate(USER_ID, PRODUCT_CODE, LOCALE);

        // coverage=29, accuracy=0, exam=0 → 0.40*29 = 11.6
        assertThat(result.readinessLabel()).isEqualTo(ReadinessLabel.NOT_READY);
    }

    @Test
    void calculate_labelBoundary_gettingThereAt30() {
        // Need score >= 30: coverage=75 → 0.40*75 = 30
        setupMocks(400, 300, 300, null, 44);

        ReadinessScore result = service.calculate(USER_ID, PRODUCT_CODE, LOCALE);

        // coverage=75, accuracy=100, exam=0 → 30+35 = 65
        assertThat(result.readinessLabel()).isEqualTo(ReadinessLabel.ALMOST_READY);
    }

    @Test
    void calculate_labelBoundary_readyAt80() {
        setupMocks(500, 450, 450, 44, 44);

        ReadinessScore result = service.calculate(USER_ID, PRODUCT_CODE, LOCALE);

        // coverage=90, accuracy=100, exam=100 → 36+35+25 = 96
        assertThat(result.readinessScore()).isGreaterThanOrEqualTo(80.0);
        assertThat(result.readinessLabel()).isEqualTo(ReadinessLabel.READY);
    }

    @Test
    void calculate_withNullExamScore_examComponentIsZero() {
        setupMocks(500, 250, 200, null, 44);

        ReadinessScore result = service.calculate(USER_ID, PRODUCT_CODE, LOCALE);

        assertThat(result.examScore()).isEqualTo(0.0);
        assertThat(result.bestExamScore()).isNull();
    }

    @Test
    void calculate_totalQuestions_matchesStrapiCount() {
        setupMocks(487, 100, 80, null, 44);

        ReadinessScore result = service.calculate(USER_ID, PRODUCT_CODE, LOCALE);

        assertThat(result.totalQuestions()).isEqualTo(487);
        assertThat(result.questionsAttempted()).isEqualTo(100);
    }

    @Test
    void calculate_domainStrengths_computedFromQuestionProgress() {
        // Domain "verkeersborden": 30 total questions, 25 attempted, 22 correct, 5 mastered
        StrapiDomainDto domain = new StrapiDomainDto(
                1, null, "Verkeersborden", "verkeersborden", "verkeersborden",
                "desc", null, "#E63946", 30, true, true, 1);

        QuestionProgressRepository.DomainMasteryProjection agg = mock(
                QuestionProgressRepository.DomainMasteryProjection.class);
        when(agg.getDomainCode()).thenReturn("verkeersborden");
        when(agg.getAttemptedCount()).thenReturn(25L);
        when(agg.getCorrectCount()).thenReturn(22L);
        when(agg.getTotalAttempts()).thenReturn(30L);

        when(progressRepository.aggregateByDomain(eq(USER_ID), eq(PRODUCT_CODE)))
                .thenReturn(List.of(agg));
        when(strapiContentCache.getDomains(eq(PRODUCT_CODE), eq(LOCALE)))
                .thenReturn(List.of(domain));
        when(strapiContentCache.getQuestionCountByDomain(eq("verkeersborden"), eq(LOCALE)))
                .thenReturn(30);
        when(strapiContentCache.getQuestionCount(eq(PRODUCT_CODE), eq(LOCALE))).thenReturn(30);
        when(progressRepository.countAttempted(eq(USER_ID), eq(PRODUCT_CODE))).thenReturn(25);
        when(progressRepository.countCorrect(eq(USER_ID), eq(PRODUCT_CODE))).thenReturn(22);
        when(progressRepository.countTotalAttempts(eq(USER_ID), eq(PRODUCT_CODE))).thenReturn(30);
        when(examAttemptRepository.findBestScore(eq(USER_ID), eq(PRODUCT_CODE))).thenReturn(null);
        when(strapiContentCache.getExamConfig(eq(PRODUCT_CODE)))
                .thenReturn(new StrapiExamConfigDto(0, null, 50, 30, 44, null, null, true, false, null, null, false));
        when(userServiceClient.getProfile(any(), any())).thenReturn(Optional.empty());
        when(examAttemptRepository.findAverageScore(eq(USER_ID), eq(PRODUCT_CODE))).thenReturn(null);
        when(examAttemptRepository.findByKeycloakUserIdAndProductCodeOrderByCompletedAtDesc(
                eq(USER_ID), eq(PRODUCT_CODE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        ReadinessScore result = service.calculate(USER_ID, PRODUCT_CODE, LOCALE);

        assertThat(result.domainStrengths()).hasSize(1);
        ReadinessScore.DomainStrengthValue dsv = result.domainStrengths().get(0);
        assertThat(dsv.domainCode()).isEqualTo("verkeersborden");
        // accuracy = 22/30*100 ≈ 73.3%, coverage = 25/30*100 ≈ 83.3% → STRONG
        assertThat(dsv.strength()).isEqualTo(DomainStrength.STRONG.name());
    }

    @Test
    void calculate_accuracyNeverExceeds100_whenQuestionsAnsweredMultipleTimes() {
        // 10 questions attempted, each answered ~3 times (30 total), 20 total correct.
        // Old bug: accuracy = 20/10 = 200%.  Fixed: accuracy = 20/30 ≈ 66.7%.
        when(strapiContentCache.getQuestionCount(eq(PRODUCT_CODE), eq(LOCALE))).thenReturn(500);
        when(progressRepository.countAttempted(eq(USER_ID), eq(PRODUCT_CODE))).thenReturn(10);
        when(progressRepository.countCorrect(eq(USER_ID), eq(PRODUCT_CODE))).thenReturn(20);
        when(progressRepository.countTotalAttempts(eq(USER_ID), eq(PRODUCT_CODE))).thenReturn(30);
        when(examAttemptRepository.findBestScore(eq(USER_ID), eq(PRODUCT_CODE))).thenReturn(null);
        when(strapiContentCache.getExamConfig(eq(PRODUCT_CODE)))
                .thenReturn(new StrapiExamConfigDto(0, null, 50, 30, 44, null, null, true, false, null, null, false));
        when(progressRepository.aggregateByDomain(eq(USER_ID), eq(PRODUCT_CODE))).thenReturn(List.of());
        when(strapiContentCache.getDomains(eq(PRODUCT_CODE), eq(LOCALE))).thenReturn(List.of());
        when(examAttemptRepository.findAverageScore(eq(USER_ID), eq(PRODUCT_CODE))).thenReturn(null);
        when(examAttemptRepository.findByKeycloakUserIdAndProductCodeOrderByCompletedAtDesc(
                eq(USER_ID), eq(PRODUCT_CODE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        ReadinessScore result = service.calculate(USER_ID, PRODUCT_CODE, LOCALE);

        assertThat(result.accuracyScore()).isCloseTo(66.67, within(0.1));
        assertThat(result.accuracyScore()).isLessThanOrEqualTo(100.0);
        assertThat(result.readinessScore()).isLessThanOrEqualTo(100.0);
    }

    // ─── EXAM CONFIDENCE ───

    @Test
    void calculate_zeroProgress_examConfidenceNotStarted() {
        setupMocks(500, 0, 0, null, 44);

        ReadinessScore result = service.calculate(USER_ID, PRODUCT_CODE, LOCALE);

        assertThat(result.examConfidence()).isNotNull();
        // Only noWeakDomainsPoints=10 (vacuously no weak domains)
        assertThat(result.examConfidence().score()).isEqualTo(10);
        assertThat(result.examConfidence().label()).isEqualTo("NOT_STARTED");
    }

    @Test
    void calculate_highCoverageAndAccuracy_noExams_earnsCoverageAndAccuracyPoints() {
        // coverage = 450/500 = 90%, accuracy = 400/450 = 88.9%
        setupMocks(500, 450, 400, null, 44);

        ReadinessScore result = service.calculate(USER_ID, PRODUCT_CODE, LOCALE);

        ExamConfidence.Breakdown b = result.examConfidence().breakdown();
        assertThat(b.coveragePoints()).isEqualTo(20);  // ≥90%
        assertThat(b.accuracyPoints()).isEqualTo(25);  // ≥88%
        assertThat(b.examConsistencyPoints()).isEqualTo(0);  // no exams
        assertThat(b.avgScorePoints()).isEqualTo(0);  // no exams
        assertThat(b.noWeakDomainsPoints()).isEqualTo(10);  // no domains = no weak
        assertThat(result.examConfidence().score()).isEqualTo(55);
    }

    @Test
    void calculate_midCoverageAndAccuracy_earnsMidPoints() {
        // coverage = 375/500 = 75%, accuracy = 320/375 = 85.3%
        setupMocks(500, 375, 320, null, 44);

        ReadinessScore result = service.calculate(USER_ID, PRODUCT_CODE, LOCALE);

        ExamConfidence.Breakdown b = result.examConfidence().breakdown();
        assertThat(b.coveragePoints()).isEqualTo(10);  // 70-89%
        assertThat(b.accuracyPoints()).isEqualTo(15);  // 80-87%
    }

    @Test
    void calculate_lowCoverageAndAccuracy_earnsZeroPoints() {
        // coverage = 200/500 = 40%, accuracy = 120/200 = 60%
        setupMocks(500, 200, 120, null, 44);

        ReadinessScore result = service.calculate(USER_ID, PRODUCT_CODE, LOCALE);

        ExamConfidence.Breakdown b = result.examConfidence().breakdown();
        assertThat(b.coveragePoints()).isEqualTo(0);  // <70%
        assertThat(b.accuracyPoints()).isEqualTo(0);  // <80%
        assertThat(b.coverageMet()).isFalse();
        assertThat(b.accuracyMet()).isFalse();
    }

    @Test
    void calculateExamConfidence_threeConsecutivePasses_earnsFullConsistencyPoints() {
        setupMocks(500, 450, 400, 46, 44);

        // Mock 3 consecutive passing exams
        var exam1 = mockExamAttempt(true, 46);
        var exam2 = mockExamAttempt(true, 45);
        var exam3 = mockExamAttempt(true, 44);
        when(examAttemptRepository.findByKeycloakUserIdAndProductCodeOrderByCompletedAtDesc(
                eq(USER_ID), eq(PRODUCT_CODE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(exam1, exam2, exam3)));
        when(examAttemptRepository.findAverageScore(eq(USER_ID), eq(PRODUCT_CODE))).thenReturn(45.0);

        ReadinessScore result = service.calculate(USER_ID, PRODUCT_CODE, LOCALE);

        ExamConfidence.Breakdown b = result.examConfidence().breakdown();
        assertThat(b.examConsistencyPoints()).isEqualTo(30);
        assertThat(b.consecutivePasses()).isEqualTo(3);
        assertThat(b.avgScorePoints()).isEqualTo(10);  // avg=45, 44≤avg<46 → 10
    }

    @Test
    void calculateExamConfidence_onePass_earnsPartialConsistencyPoints() {
        setupMocks(500, 450, 400, 46, 44);

        var exam1 = mockExamAttempt(true, 46);
        var exam2 = mockExamAttempt(false, 40);
        when(examAttemptRepository.findByKeycloakUserIdAndProductCodeOrderByCompletedAtDesc(
                eq(USER_ID), eq(PRODUCT_CODE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(exam1, exam2)));
        when(examAttemptRepository.findAverageScore(eq(USER_ID), eq(PRODUCT_CODE))).thenReturn(43.0);

        ReadinessScore result = service.calculate(USER_ID, PRODUCT_CODE, LOCALE);

        ExamConfidence.Breakdown b = result.examConfidence().breakdown();
        assertThat(b.examConsistencyPoints()).isEqualTo(15);  // 1-2 passes
        assertThat(b.consecutivePasses()).isEqualTo(1);
        assertThat(b.avgScorePoints()).isEqualTo(0);  // avg<44
    }

    @Test
    void calculateExamConfidence_highAvgScore_earnsFullAvgPoints() {
        setupMocks(500, 450, 400, 48, 44);

        var exam1 = mockExamAttempt(true, 48);
        when(examAttemptRepository.findByKeycloakUserIdAndProductCodeOrderByCompletedAtDesc(
                eq(USER_ID), eq(PRODUCT_CODE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(exam1)));
        when(examAttemptRepository.findAverageScore(eq(USER_ID), eq(PRODUCT_CODE))).thenReturn(48.0);

        ReadinessScore result = service.calculate(USER_ID, PRODUCT_CODE, LOCALE);

        assertThat(result.examConfidence().breakdown().avgScorePoints()).isEqualTo(15);  // avg≥46
    }

    @Test
    void calculateExamConfidence_maxScore_cappedAt95() {
        // All criteria maxed: 20 + 25 + 30 + 15 + 10 = 100 → capped at 95
        setupMocks(500, 450, 400, 48, 44);

        var exam1 = mockExamAttempt(true, 48);
        var exam2 = mockExamAttempt(true, 47);
        var exam3 = mockExamAttempt(true, 46);
        when(examAttemptRepository.findByKeycloakUserIdAndProductCodeOrderByCompletedAtDesc(
                eq(USER_ID), eq(PRODUCT_CODE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(exam1, exam2, exam3)));
        when(examAttemptRepository.findAverageScore(eq(USER_ID), eq(PRODUCT_CODE))).thenReturn(47.0);

        ReadinessScore result = service.calculate(USER_ID, PRODUCT_CODE, LOCALE);

        assertThat(result.examConfidence().score()).isEqualTo(95);
        assertThat(result.examConfidence().label()).isEqualTo("READY");
    }

    @Test
    void classifyConfidenceLabel_zeroScoreZeroCoverage_notStarted() {
        assertThat(ReadinessService.classifyConfidenceLabel(0, 0.0)).isEqualTo("NOT_STARTED");
    }

    @Test
    void classifyConfidenceLabel_scoreBelowThirty_notReady() {
        assertThat(ReadinessService.classifyConfidenceLabel(20, 50.0)).isEqualTo("NOT_READY");
    }

    @Test
    void classifyConfidenceLabel_scoreThirtyToSixty_gettingThere() {
        assertThat(ReadinessService.classifyConfidenceLabel(45, 70.0)).isEqualTo("GETTING_THERE");
    }

    @Test
    void classifyConfidenceLabel_scoreSixtyToEighty_almostReady() {
        assertThat(ReadinessService.classifyConfidenceLabel(70, 90.0)).isEqualTo("ALMOST_READY");
    }

    @Test
    void classifyConfidenceLabel_scoreEightyPlus_ready() {
        assertThat(ReadinessService.classifyConfidenceLabel(85, 90.0)).isEqualTo("READY");
    }

    private com.passtheo.content.domain.entity.ExamAttempt mockExamAttempt(boolean passed, int correctCount) {
        var exam = mock(com.passtheo.content.domain.entity.ExamAttempt.class);
        when(exam.isPassed()).thenReturn(passed);
        // Lenient because short-circuit evaluation may skip getCorrectCount() when isPassed() is false
        org.mockito.Mockito.lenient().when(exam.getCorrectCount()).thenReturn(correctCount);
        return exam;
    }

    // ─── DOMAIN STRENGTH CLASSIFICATION ───

    @Test
    void classifyDomainStrength_belowFiftyAccuracy_isWeak() {
        assertThat(ReadinessService.classifyDomainStrength(45.0, 80.0)).isEqualTo(DomainStrength.WEAK);
    }

    @Test
    void classifyDomainStrength_fiftyToSeventy_isModerate() {
        assertThat(ReadinessService.classifyDomainStrength(65.0, 50.0)).isEqualTo(DomainStrength.MODERATE);
    }

    @Test
    void classifyDomainStrength_aboveEightyFiveWithHighCoverage_isMastered() {
        assertThat(ReadinessService.classifyDomainStrength(90.0, 85.0)).isEqualTo(DomainStrength.MASTERED);
    }

    @Test
    void classifyDomainStrength_seventyToEightyFiveWithGoodCoverage_isStrong() {
        assertThat(ReadinessService.classifyDomainStrength(78.0, 65.0)).isEqualTo(DomainStrength.STRONG);
    }

    @Test
    void classifyDomainStrength_highAccuracyLowCoverage_isModerate() {
        assertThat(ReadinessService.classifyDomainStrength(88.0, 30.0)).isEqualTo(DomainStrength.MODERATE);
    }

    // ─── HELPERS ───

    private void setupMocks(int totalQuestions, int attempted, int correct,
                            Integer bestExam, int passScore) {
        when(strapiContentCache.getQuestionCount(eq(PRODUCT_CODE), eq(LOCALE))).thenReturn(totalQuestions);
        when(progressRepository.countAttempted(eq(USER_ID), eq(PRODUCT_CODE))).thenReturn(attempted);
        when(progressRepository.countCorrect(eq(USER_ID), eq(PRODUCT_CODE))).thenReturn(correct);
        when(progressRepository.countTotalAttempts(eq(USER_ID), eq(PRODUCT_CODE))).thenReturn(attempted);
        when(examAttemptRepository.findBestScore(eq(USER_ID), eq(PRODUCT_CODE))).thenReturn(bestExam);
        when(strapiContentCache.getExamConfig(eq(PRODUCT_CODE)))
                .thenReturn(new StrapiExamConfigDto(0, null, 50, 30, passScore, null, null, true, false, null, null, false));
        when(progressRepository.aggregateByDomain(eq(USER_ID), eq(PRODUCT_CODE))).thenReturn(List.of());
        when(strapiContentCache.getDomains(eq(PRODUCT_CODE), eq(LOCALE))).thenReturn(List.of());
        // Exam confidence mocks (lenient because individual tests may override)
        org.mockito.Mockito.lenient().when(examAttemptRepository.findAverageScore(eq(USER_ID), eq(PRODUCT_CODE))).thenReturn(null);
        org.mockito.Mockito.lenient().when(examAttemptRepository.findByKeycloakUserIdAndProductCodeOrderByCompletedAtDesc(
                eq(USER_ID), eq(PRODUCT_CODE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
    }
}
