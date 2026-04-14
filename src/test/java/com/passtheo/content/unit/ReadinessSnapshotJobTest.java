package com.passtheo.content.unit;

import com.passtheo.content.domain.entity.ReadinessSnapshot;
import com.passtheo.content.integration.strapi.StrapiContentCache;
import com.passtheo.content.integration.strapi.dto.StrapiExamConfigDto;
import com.passtheo.content.repository.ExamAttemptRepository;
import com.passtheo.content.repository.QuestionProgressRepository;
import com.passtheo.content.repository.QuestionProgressRepository.UserProductProjection;
import com.passtheo.content.repository.ReadinessSnapshotRepository;
import com.passtheo.content.scheduler.ReadinessSnapshotJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ReadinessSnapshotJob — score computation logic.
 */
@ExtendWith(MockitoExtension.class)
class ReadinessSnapshotJobTest {

    @Mock private QuestionProgressRepository progressRepository;
    @Mock private ReadinessSnapshotRepository snapshotRepository;
    @Mock private ExamAttemptRepository examAttemptRepository;
    @Mock private StrapiContentCache strapiContentCache;

    private ReadinessSnapshotJob job;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String PRODUCT_CODE = "auto-b";
    private static final LocalDate TODAY = LocalDate.of(2026, 4, 14);

    @BeforeEach
    void setUp() {
        job = new ReadinessSnapshotJob(progressRepository, snapshotRepository,
                examAttemptRepository, strapiContentCache);
    }

    @Test
    void computeSnapshot_typicalProgress_correctWeightedScore() {
        UserProductProjection up = stubProjection(TENANT_ID, USER_ID, PRODUCT_CODE);

        // 200 of 500 questions attempted → 40% coverage
        when(progressRepository.countAttempted(USER_ID, PRODUCT_CODE)).thenReturn(200);
        when(progressRepository.countCorrect(USER_ID, PRODUCT_CODE)).thenReturn(160);
        when(progressRepository.countTotalAttempts(USER_ID, PRODUCT_CODE)).thenReturn(200);
        when(strapiContentCache.getQuestionCount(PRODUCT_CODE, "nl")).thenReturn(500);
        when(examAttemptRepository.findBestScore(USER_ID, PRODUCT_CODE)).thenReturn(40);
        when(strapiContentCache.getExamConfig(eq(PRODUCT_CODE), eq("nl")))
                .thenReturn(examConfig(44));

        ReadinessSnapshot snapshot = job.computeSnapshot(up, TODAY);

        // coverage = 200/500 * 100 = 40.0
        // accuracy = 160/200 * 100 = 80.0
        // exam = min(100, 40/44 * 100) = 90.91
        // readiness = 0.40*40 + 0.35*80 + 0.25*90.91 = 16 + 28 + 22.73 = 66.73
        assertThat(snapshot.getCoverageScore().doubleValue()).isCloseTo(40.0, within(0.01));
        assertThat(snapshot.getAccuracyScore().doubleValue()).isCloseTo(80.0, within(0.01));
        assertThat(snapshot.getExamScore().doubleValue()).isCloseTo(90.91, within(0.01));
        assertThat(snapshot.getReadinessScore().doubleValue()).isCloseTo(66.73, within(0.01));
        assertThat(snapshot.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(snapshot.getKeycloakUserId()).isEqualTo(USER_ID);
        assertThat(snapshot.getProductCode()).isEqualTo(PRODUCT_CODE);
        assertThat(snapshot.getSnapshotDate()).isEqualTo(TODAY);
        assertThat(snapshot.getQuestionsAttempted()).isEqualTo(200);
        assertThat(snapshot.getTotalQuestions()).isEqualTo(500);
        assertThat(snapshot.getBestExamScore()).isEqualTo(40);
    }

    @Test
    void computeSnapshot_noExamAttempts_examScoreZero() {
        UserProductProjection up = stubProjection(TENANT_ID, USER_ID, PRODUCT_CODE);

        when(progressRepository.countAttempted(USER_ID, PRODUCT_CODE)).thenReturn(100);
        when(progressRepository.countCorrect(USER_ID, PRODUCT_CODE)).thenReturn(70);
        when(progressRepository.countTotalAttempts(USER_ID, PRODUCT_CODE)).thenReturn(100);
        when(strapiContentCache.getQuestionCount(PRODUCT_CODE, "nl")).thenReturn(500);
        when(examAttemptRepository.findBestScore(USER_ID, PRODUCT_CODE)).thenReturn(null);
        when(strapiContentCache.getExamConfig(eq(PRODUCT_CODE), eq("nl")))
                .thenReturn(examConfig(44));

        ReadinessSnapshot snapshot = job.computeSnapshot(up, TODAY);

        // coverage = 100/500*100 = 20.0, accuracy = 70/100*100 = 70.0, exam = 0.0
        // readiness = 0.40*20 + 0.35*70 + 0.25*0 = 8 + 24.5 + 0 = 32.5
        assertThat(snapshot.getExamScore().doubleValue()).isCloseTo(0.0, within(0.01));
        assertThat(snapshot.getReadinessScore().doubleValue()).isCloseTo(32.5, within(0.01));
        assertThat(snapshot.getBestExamScore()).isNull();
    }

    @Test
    void computeSnapshot_zeroAttempts_allScoresZero() {
        UserProductProjection up = stubProjection(TENANT_ID, USER_ID, PRODUCT_CODE);

        when(progressRepository.countAttempted(USER_ID, PRODUCT_CODE)).thenReturn(0);
        when(progressRepository.countCorrect(USER_ID, PRODUCT_CODE)).thenReturn(0);
        when(progressRepository.countTotalAttempts(USER_ID, PRODUCT_CODE)).thenReturn(0);
        when(strapiContentCache.getQuestionCount(PRODUCT_CODE, "nl")).thenReturn(500);
        when(examAttemptRepository.findBestScore(USER_ID, PRODUCT_CODE)).thenReturn(null);
        when(strapiContentCache.getExamConfig(eq(PRODUCT_CODE), eq("nl"))).thenReturn(null);

        ReadinessSnapshot snapshot = job.computeSnapshot(up, TODAY);

        assertThat(snapshot.getCoverageScore().doubleValue()).isCloseTo(0.0, within(0.01));
        assertThat(snapshot.getAccuracyScore().doubleValue()).isCloseTo(0.0, within(0.01));
        assertThat(snapshot.getExamScore().doubleValue()).isCloseTo(0.0, within(0.01));
        assertThat(snapshot.getReadinessScore().doubleValue()).isCloseTo(0.0, within(0.01));
    }

    @Test
    void computeSnapshot_attemptedExceedsTotalQuestions_clampedToCeiling() {
        UserProductProjection up = stubProjection(TENANT_ID, USER_ID, PRODUCT_CODE);

        // Attempted 600 but only 500 active questions (deactivated questions)
        when(progressRepository.countAttempted(USER_ID, PRODUCT_CODE)).thenReturn(600);
        when(progressRepository.countCorrect(USER_ID, PRODUCT_CODE)).thenReturn(500);
        when(progressRepository.countTotalAttempts(USER_ID, PRODUCT_CODE)).thenReturn(600);
        when(strapiContentCache.getQuestionCount(PRODUCT_CODE, "nl")).thenReturn(500);
        when(examAttemptRepository.findBestScore(USER_ID, PRODUCT_CODE)).thenReturn(null);
        when(strapiContentCache.getExamConfig(eq(PRODUCT_CODE), eq("nl"))).thenReturn(null);

        ReadinessSnapshot snapshot = job.computeSnapshot(up, TODAY);

        // coverage clamped: min(600,500)/500*100 = 100.0
        assertThat(snapshot.getCoverageScore().doubleValue()).isCloseTo(100.0, within(0.01));
        assertThat(snapshot.getQuestionsAttempted()).isEqualTo(500);
    }

    private static StrapiExamConfigDto examConfig(int passScore) {
        return new StrapiExamConfigDto(
                1, "doc-1", "CBR Exam", "Theory exam", List.of(),
                50, 30, passScore, null, null, true, false,
                List.of(), Map.of(), false
        );
    }

    private static UserProductProjection stubProjection(UUID tenantId, UUID userId, String productCode) {
        return new UserProductProjection() {
            @Override public UUID getTenantId() { return tenantId; }
            @Override public UUID getKeycloakUserId() { return userId; }
            @Override public String getProductCode() { return productCode; }
        };
    }
}
