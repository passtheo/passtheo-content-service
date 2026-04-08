package com.passtheo.content.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.passtheo.content.domain.entity.ExamAttempt;
import com.passtheo.content.domain.enums.ExamType;
import com.passtheo.content.dto.request.SubmitExamRequest;
import com.passtheo.content.dto.response.ExamResultDto;
import com.passtheo.content.integration.strapi.StrapiContentCache;
import com.passtheo.content.integration.strapi.dto.StrapiQuestionDto;
import com.passtheo.content.integration.strapi.dto.StrapiRelationDto;
import com.passtheo.content.repository.ExamAnswerRepository;
import com.passtheo.content.repository.ExamAttemptRepository;
import com.passtheo.content.service.AchievementService;
import com.passtheo.content.service.AnswerProcessingService;
import com.passtheo.content.service.MockExamService;
import com.passtheo.content.service.ReadinessService;
import com.passtheo.content.domain.enums.ReadinessLabel;
import com.passtheo.content.domain.valueobject.ExamConfidence;
import com.passtheo.content.domain.valueobject.ReadinessScore;
import com.passtheo.content.domain.enums.ConfidenceLabel;
import com.passtheo.content.domain.enums.RecommendationKey;
import com.passtheo.shared.core.context.TenantContext;
import com.passtheo.shared.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for MockExamService — deactivated question handling during exam grading.
 */
@ExtendWith(MockitoExtension.class)
class MockExamServiceTest {

    @Mock private ExamAttemptRepository examAttemptRepository;
    @Mock private ExamAnswerRepository examAnswerRepository;
    @Mock private StrapiContentCache strapiContentCache;
    @Mock private AnswerProcessingService answerProcessingService;
    @Mock private AchievementService achievementService;
    @Mock private ReadinessService readinessService;
    @Mock private OutboxEventRepository outboxEventRepository;

    private MockExamService service;

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID EXAM_ID = UUID.randomUUID();
    private static final String PRODUCT_CODE = "auto-b";

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        service = new MockExamService(
                examAttemptRepository, examAnswerRepository, strapiContentCache,
                answerProcessingService, achievementService, readinessService,
                outboxEventRepository, new ObjectMapper()
        );
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void submitExam_deactivatedQuestion_recordedAsIncorrectWithWrongAnswer() {
        ExamAttempt attempt = buildAttempt();
        when(examAttemptRepository.findByIdAndKeycloakUserId(EXAM_ID, USER_ID))
                .thenReturn(Optional.of(attempt));
        when(examAttemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(examAnswerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Question q1 is active, q2 is deactivated (returns null)
        StrapiQuestionDto activeQ = buildQuestion("q1", "voorrang");
        when(strapiContentCache.getQuestion("q1", "nl")).thenReturn(activeQ);
        when(strapiContentCache.getQuestion("q2", "nl")).thenReturn(null);
        when(answerProcessingService.gradeAnswer(eq(activeQ), any())).thenReturn(true);
        when(answerProcessingService.buildCorrectAnswer(activeQ)).thenReturn(Map.of("selectedOptionId", "1"));

        when(readinessService.calculate(eq(USER_ID), eq(PRODUCT_CODE), eq("nl")))
                .thenReturn(buildMinimalReadiness());
        when(achievementService.checkAchievements(USER_ID, PRODUCT_CODE)).thenReturn(List.of());

        SubmitExamRequest request = new SubmitExamRequest(List.of(
                new SubmitExamRequest.ExamAnswerItem("q1", Map.of("selectedOptionId", "1"), 5000),
                new SubmitExamRequest.ExamAnswerItem("q2", Map.of("selectedOptionId", "2"), 3000)
        ));

        ExamResultDto result = service.submitExam(USER_ID, EXAM_ID, request, "nl");

        // q1 correct, q2 treated as incorrect → 1/2 = 50%
        assertThat(result.correctCount()).isEqualTo(1);
        assertThat(result.totalQuestions()).isEqualTo(2);
        // Wrong answers list should include the deactivated question
        assertThat(result.wrongAnswers()).hasSize(1);
        assertThat(result.wrongAnswers().get(0).strapiQuestionId()).isEqualTo("q2");
        assertThat(result.wrongAnswers().get(0).questionText()).isEqualTo("This question is no longer available");
    }

    private ExamAttempt buildAttempt() {
        ExamAttempt attempt = new ExamAttempt();
        attempt.setId(EXAM_ID);
        attempt.setTenantId(TENANT_ID);
        attempt.setKeycloakUserId(USER_ID);
        attempt.setProductCode(PRODUCT_CODE);
        attempt.setExamType(ExamType.MOCK_EXAM);
        attempt.setTotalQuestions(2);
        attempt.setCorrectCount(0);
        attempt.setPassScore(44);
        attempt.setPassed(false);
        attempt.setScorePercent(BigDecimal.ZERO);
        attempt.setTimeTakenSeconds(0);
        attempt.setTimeLimitSeconds(1800);
        attempt.setDomainBreakdown("{}");
        attempt.setStartedAt(Instant.now().minusSeconds(60));
        attempt.setCompletedAt(Instant.now());
        return attempt;
    }

    private StrapiQuestionDto buildQuestion(String docId, String domainCode) {
        return new StrapiQuestionDto(
                1, docId, "Question " + docId,
                "multiple_choice", "medium",
                null, null, null, null, 1, false, false, null, null,
                List.of(), null, null, null, null, null,
                new StrapiRelationDto(0, null, domainCode, null),
                new StrapiRelationDto(0, null, "topic", null),
                null);
    }

    private ReadinessScore buildMinimalReadiness() {
        return new ReadinessScore(
                0.0, 0.0, 0.0, 0.0, ReadinessLabel.NOT_READY,
                0, 0, null, 44, List.of(), null, null,
                new ExamConfidence(0, ConfidenceLabel.NOT_READY, RecommendationKey.KEEP_PRACTICING,
                        new ExamConfidence.Breakdown(0, 0, 0, 0, 0, false, false, 0, List.of()))
        );
    }
}
