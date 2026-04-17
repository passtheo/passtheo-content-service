package com.passtheo.content.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.passtheo.content.domain.entity.SessionAnswer;
import com.passtheo.content.domain.entity.StudySession;
import com.passtheo.content.domain.enums.SessionStatus;
import com.passtheo.content.domain.enums.SessionType;
import com.passtheo.content.dto.response.ActiveSessionDto;
import com.passtheo.content.dto.response.AnsweredQuestionFullDto;
import com.passtheo.content.dto.response.SessionDto;
import com.passtheo.content.integration.strapi.StrapiContentCache;
import com.passtheo.content.integration.strapi.dto.StrapiDomainDto;
import com.passtheo.content.integration.strapi.dto.StrapiQuestionDto;
import com.passtheo.content.integration.strapi.dto.StrapiRelationDto;
import com.passtheo.shared.outbox.repository.OutboxEventRepository;
import com.passtheo.content.repository.QuestionProgressRepository;
import com.passtheo.content.repository.QuestionReportRepository;
import com.passtheo.content.repository.SessionAnswerRepository;
import com.passtheo.content.repository.StudyPlanDayRepository;
import com.passtheo.content.repository.StudyPlanRepository;
import com.passtheo.content.repository.StudySessionRepository;
import com.passtheo.content.service.AchievementService;
import com.passtheo.content.service.AnswerProcessingService;
import com.passtheo.content.service.PracticeSessionService;
import com.passtheo.content.service.QuestionSelectionService;
import com.passtheo.content.service.StreakService;
import com.passtheo.content.service.XpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PracticeSessionService.getActiveSession().
 */
@ExtendWith(MockitoExtension.class)
class PracticeSessionServiceActiveSessionTest {

    @Mock private StudySessionRepository sessionRepository;
    @Mock private SessionAnswerRepository answerRepository;
    @Mock private QuestionProgressRepository progressRepository;
    @Mock private QuestionSelectionService questionSelectionService;
    @Mock private AnswerProcessingService answerProcessingService;
    @Mock private StreakService streakService;
    @Mock private AchievementService achievementService;
    @Mock private XpService xpService;
    @Mock private StrapiContentCache strapiContentCache;
    @Mock private StudyPlanRepository planRepository;
    @Mock private StudyPlanDayRepository planDayRepository;
    @Mock private QuestionReportRepository questionReportRepository;
    @Mock private OutboxEventRepository outboxEventRepository;

    private PracticeSessionService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String PRODUCT_CODE = "auto-b";

    @BeforeEach
    void setUp() {
        service = new PracticeSessionService(
                sessionRepository, answerRepository, progressRepository,
                questionSelectionService, answerProcessingService, streakService,
                achievementService, xpService, strapiContentCache, planRepository, planDayRepository,
                questionReportRepository, outboxEventRepository, new ObjectMapper()
        );
    }

    @Test
    void getActiveSession_sessionExists_returnsDto() {
        StudySession session = new StudySession(USER_ID, PRODUCT_CODE, "voorrang", null,
                SessionType.PRACTICE, 20, "nl");
        session.setId(UUID.randomUUID());
        session.setAnsweredCount(7);

        when(sessionRepository.findFirstByKeycloakUserIdAndProductCodeAndStatusOrderByLastActivityAtDesc(
                USER_ID, PRODUCT_CODE, SessionStatus.IN_PROGRESS))
                .thenReturn(Optional.of(session));

        StrapiDomainDto domain = new StrapiDomainDto(
                1, "doc1", "Voorrang", "voorrang", "voorrang",
                "desc", "icon.svg", "#EF4444", 78, true, true, 0);
        when(strapiContentCache.getDomains(PRODUCT_CODE, "nl"))
                .thenReturn(List.of(domain));

        ActiveSessionDto result = service.getActiveSession(USER_ID, PRODUCT_CODE, "nl");

        assertThat(result).isNotNull();
        assertThat(result.sessionId()).isEqualTo(session.getId());
        assertThat(result.domainCode()).isEqualTo("voorrang");
        assertThat(result.domainName()).isEqualTo("Voorrang");
        assertThat(result.sessionType()).isEqualTo("PRACTICE");
        assertThat(result.totalQuestions()).isEqualTo(20);
        assertThat(result.answeredQuestions()).isEqualTo(7);
        assertThat(result.progressPercent()).isEqualTo(35);
    }

    @Test
    void getActiveSession_noSession_returnsNull() {
        when(sessionRepository.findFirstByKeycloakUserIdAndProductCodeAndStatusOrderByLastActivityAtDesc(
                USER_ID, PRODUCT_CODE, SessionStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());

        ActiveSessionDto result = service.getActiveSession(USER_ID, PRODUCT_CODE, "nl");

        assertThat(result).isNull();
    }

    @Test
    void getSession_allRemainingQuestionsDeactivated_adjustsTotalQuestions() {
        // Session has 10 questions, 5 answered. Remaining 5 are all deactivated.
        // getSession() should return totalQuestions == answeredCount (5) and currentQuestion == null.
        StudySession session = new StudySession(USER_ID, PRODUCT_CODE, "voorrang", null,
                SessionType.PRACTICE, 10, "nl");
        session.setId(UUID.randomUUID());
        session.setAnsweredCount(5);
        session.setQuestionIdList(List.of("q1", "q2", "q3", "q4", "q5", "q6", "q7", "q8", "q9", "q10"));

        when(sessionRepository.findByIdAndKeycloakUserId(session.getId(), USER_ID))
                .thenReturn(Optional.of(session));
        // All remaining questions (q6-q10) return null from Strapi
        when(answerRepository.findBySessionIdOrderByQuestionOrderAsc(session.getId()))
                .thenReturn(List.of());

        SessionDto result = service.getSession(USER_ID, session.getId());

        assertThat(result).isNotNull();
        assertThat(result.currentQuestion()).isNull();
        // totalQuestions should be adjusted down to answeredCount
        assertThat(result.totalQuestions()).isEqualTo(5);
    }

    @Test
    void getSession_withAnsweredQuestions_populatesAnsweredQuestionContents() {
        // Session with 5 questions, 2 answered (1 correct multiple_choice, 1 skipped).
        // getSession() should return both the legacy `answeredQuestions` summary list
        // AND the new `answeredQuestionContents` list with full question content +
        // user's answer + revealed correct answer + explanation, ordered by questionOrder.
        UUID sessionId = UUID.randomUUID();
        StudySession session = new StudySession(USER_ID, PRODUCT_CODE, "voorrang", null,
                SessionType.PRACTICE, 5, "nl");
        session.setId(sessionId);
        session.setAnsweredCount(2);
        session.setQuestionIdList(List.of("doc-q1", "doc-q2", "doc-q3", "doc-q4", "doc-q5"));

        when(sessionRepository.findByIdAndKeycloakUserId(sessionId, USER_ID))
                .thenReturn(Optional.of(session));

        SessionAnswer correctAnswer = new SessionAnswer(
                sessionId, USER_ID, "doc-q1", 1, "multiple_choice", true,
                "{\"selectedOptionId\":\"opt-a\"}",
                "{\"selectedOptionId\":\"opt-a\"}",
                4200, 1);
        SessionAnswer skippedAnswer = new SessionAnswer(
                sessionId, USER_ID, "doc-q2", 1, "multiple_choice", false,
                "null",
                "{\"selectedOptionId\":\"opt-b\"}",
                1500, 2);
        when(answerRepository.findBySessionIdOrderByQuestionOrderAsc(sessionId))
                .thenReturn(List.of(correctAnswer, skippedAnswer));

        // Stub Strapi content for each answered question AND for the next
        // unanswered question (index = answeredCount = 2 → questionId "doc-q3").
        when(strapiContentCache.getQuestion("doc-q1", "nl"))
                .thenReturn(buildStrapiQuestion("doc-q1", "What is Q1?"));
        when(strapiContentCache.getQuestion("doc-q2", "nl"))
                .thenReturn(buildStrapiQuestion("doc-q2", "What is Q2?"));
        when(strapiContentCache.getQuestion("doc-q3", "nl"))
                .thenReturn(buildStrapiQuestion("doc-q3", "What is Q3?"));

        SessionDto result = service.getSession(USER_ID, sessionId);

        assertThat(result).isNotNull();
        assertThat(result.answeredCount()).isEqualTo(2);
        assertThat(result.totalQuestions()).isEqualTo(5);
        assertThat(result.currentQuestion()).isNotNull();
        assertThat(result.currentQuestion().strapiQuestionId()).isEqualTo("doc-q3");

        // Legacy summary list still present.
        assertThat(result.answeredQuestions()).hasSize(2);
        assertThat(result.answeredQuestions().get(0).questionOrder()).isEqualTo(1);
        assertThat(result.answeredQuestions().get(0).correct()).isTrue();
        assertThat(result.answeredQuestions().get(1).skipped()).isTrue();

        // New full-content list, ordered by questionOrder ascending.
        assertThat(result.answeredQuestionContents()).hasSize(2);

        AnsweredQuestionFullDto first = result.answeredQuestionContents().get(0);
        assertThat(first.questionOrder()).isEqualTo(1);
        assertThat(first.question().strapiQuestionId()).isEqualTo("doc-q1");
        assertThat(first.question().questionText()).isEqualTo("What is Q1?");
        assertThat(first.question().answerOptions()).isNotEmpty();
        assertThat(first.userAnswer()).containsEntry("selectedOptionId", "opt-a");
        assertThat(first.correctAnswer()).containsEntry("selectedOptionId", "opt-a");
        assertThat(first.explanation()).isNotNull();
        assertThat(first.explanation().text()).isNotBlank();
        assertThat(first.isCorrect()).isTrue();
        assertThat(first.skipped()).isFalse();
        assertThat(first.timeTakenMs()).isEqualTo(4200);

        AnsweredQuestionFullDto second = result.answeredQuestionContents().get(1);
        assertThat(second.questionOrder()).isEqualTo(2);
        assertThat(second.question().strapiQuestionId()).isEqualTo("doc-q2");
        assertThat(second.userAnswer()).isNull();
        assertThat(second.correctAnswer()).containsEntry("selectedOptionId", "opt-b");
        assertThat(second.isCorrect()).isFalse();
        assertThat(second.skipped()).isTrue();
        assertThat(second.timeTakenMs()).isEqualTo(1500);
    }

    @Test
    void getSession_deactivatedAnsweredQuestion_filteredFromContentsKeptInSummary() {
        // Session with 5 questions, 2 answered — Q1 loads normally, Q2 has been
        // deactivated/deleted from Strapi. The summary list must still contain
        // both entries (client needs counters), but answeredQuestionContents
        // must omit Q2 since its content can no longer be rendered.
        UUID sessionId = UUID.randomUUID();
        StudySession session = new StudySession(USER_ID, PRODUCT_CODE, "voorrang", null,
                SessionType.PRACTICE, 5, "nl");
        session.setId(sessionId);
        session.setAnsweredCount(2);
        session.setQuestionIdList(List.of("doc-q1", "doc-q2", "doc-q3", "doc-q4", "doc-q5"));

        when(sessionRepository.findByIdAndKeycloakUserId(sessionId, USER_ID))
                .thenReturn(Optional.of(session));

        SessionAnswer answered1 = new SessionAnswer(
                sessionId, USER_ID, "doc-q1", 1, "multiple_choice", true,
                "{\"selectedOptionId\":\"opt-a\"}",
                "{\"selectedOptionId\":\"opt-a\"}",
                4200, 1);
        SessionAnswer answered2Deactivated = new SessionAnswer(
                sessionId, USER_ID, "doc-q2", 1, "multiple_choice", false,
                "{\"selectedOptionId\":\"opt-b\"}",
                "{\"selectedOptionId\":\"opt-a\"}",
                3100, 2);
        when(answerRepository.findBySessionIdOrderByQuestionOrderAsc(sessionId))
                .thenReturn(List.of(answered1, answered2Deactivated));

        when(strapiContentCache.getQuestion("doc-q1", "nl"))
                .thenReturn(buildStrapiQuestion("doc-q1", "What is Q1?"));
        when(strapiContentCache.getQuestion("doc-q2", "nl")).thenReturn(null);
        when(strapiContentCache.getQuestion("doc-q3", "nl"))
                .thenReturn(buildStrapiQuestion("doc-q3", "What is Q3?"));

        SessionDto result = service.getSession(USER_ID, sessionId);

        assertThat(result.answeredCount()).isEqualTo(2);
        assertThat(result.answeredQuestions()).hasSize(2);
        assertThat(result.answeredQuestions().get(1).questionOrder()).isEqualTo(2);
        // Only the still-live answered question surfaces in the full-content list.
        assertThat(result.answeredQuestionContents()).hasSize(1);
        assertThat(result.answeredQuestionContents().get(0).questionOrder()).isEqualTo(1);
        assertThat(result.answeredQuestionContents().get(0).question().strapiQuestionId())
                .isEqualTo("doc-q1");
    }

    private StrapiQuestionDto buildStrapiQuestion(String documentId, String questionText) {
        return new StrapiQuestionDto(
                1, documentId, questionText, "multiple_choice", "medium",
                null, null, null, null, 1, true, false, null, null,
                List.of(
                        new StrapiQuestionDto.AnswerOptionDto(1, "Option A", null, true, 1),
                        new StrapiQuestionDto.AnswerOptionDto(2, "Option B", null, false, 2)
                ),
                new StrapiQuestionDto.ExplanationDto(
                        "Because A is correct.", null, "Think carefully.",
                        null, "RVV 1990 Art. 1"),
                null, null, null, null,
                new StrapiRelationDto(0, null, "voorrang", null),
                null, null
        );
    }

    @Test
    void getActiveSession_nullDomainCode_returnsNullDomainName() {
        StudySession session = new StudySession(USER_ID, PRODUCT_CODE, null, null,
                SessionType.QUICK_QUIZ, 5, "nl");
        session.setId(UUID.randomUUID());

        when(sessionRepository.findFirstByKeycloakUserIdAndProductCodeAndStatusOrderByLastActivityAtDesc(
                USER_ID, PRODUCT_CODE, SessionStatus.IN_PROGRESS))
                .thenReturn(Optional.of(session));

        ActiveSessionDto result = service.getActiveSession(USER_ID, PRODUCT_CODE, "nl");

        assertThat(result).isNotNull();
        assertThat(result.domainCode()).isNull();
        assertThat(result.domainName()).isNull();
    }
}
