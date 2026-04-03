package com.passtheo.content.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.passtheo.content.domain.entity.StudySession;
import com.passtheo.content.domain.enums.SessionStatus;
import com.passtheo.content.domain.enums.SessionType;
import com.passtheo.content.dto.response.ActiveSessionDto;
import com.passtheo.content.integration.strapi.StrapiContentCache;
import com.passtheo.content.integration.strapi.dto.StrapiDomainDto;
import com.passtheo.shared.outbox.repository.OutboxEventRepository;
import com.passtheo.content.repository.QuestionProgressRepository;
import com.passtheo.content.repository.SessionAnswerRepository;
import com.passtheo.content.repository.StudySessionRepository;
import com.passtheo.content.service.AchievementService;
import com.passtheo.content.service.AnswerProcessingService;
import com.passtheo.content.service.PracticeSessionService;
import com.passtheo.content.service.QuestionSelectionService;
import com.passtheo.content.service.StreakService;
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
    @Mock private StrapiContentCache strapiContentCache;
    @Mock private OutboxEventRepository outboxEventRepository;

    private PracticeSessionService service;

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String PRODUCT_CODE = "auto-b";

    @BeforeEach
    void setUp() {
        service = new PracticeSessionService(
                sessionRepository, answerRepository, progressRepository,
                questionSelectionService, answerProcessingService, streakService,
                achievementService, strapiContentCache, outboxEventRepository,
                new ObjectMapper()
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
