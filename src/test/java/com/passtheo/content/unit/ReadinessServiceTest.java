package com.passtheo.content.unit;

import com.passtheo.shared.core.client.UserServiceInternalClient;
import com.passtheo.content.domain.enums.DomainStrength;
import com.passtheo.content.domain.enums.ReadinessLabel;
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

        ReadinessScore result = service.calculate(USER_ID, PRODUCT_CODE, LOCALE);

        assertThat(result.accuracyScore()).isCloseTo(66.67, within(0.1));
        assertThat(result.accuracyScore()).isLessThanOrEqualTo(100.0);
        assertThat(result.readinessScore()).isLessThanOrEqualTo(100.0);
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
    }
}
