package com.passtheo.content.unit;

import com.passtheo.content.domain.entity.EarnedAchievement;
import com.passtheo.content.domain.entity.ExamAttempt;
import com.passtheo.content.domain.entity.ReadinessSnapshot;
import com.passtheo.content.dto.response.EarnedAchievementDto;
import com.passtheo.content.integration.strapi.StrapiContentCache;
import com.passtheo.content.integration.strapi.dto.StrapiAchievementDefDto;
import com.passtheo.content.integration.strapi.dto.StrapiDomainDto;
import com.passtheo.content.repository.EarnedAchievementRepository;
import com.passtheo.content.repository.ExamAttemptRepository;
import com.passtheo.content.repository.ReadinessSnapshotRepository;
import com.passtheo.content.repository.SessionAnswerRepository;
import com.passtheo.content.repository.StudySessionRepository;
import com.passtheo.shared.outbox.repository.OutboxEventRepository;
import com.passtheo.content.repository.LessonProgressRepository;
import com.passtheo.content.repository.QuestionProgressRepository;
import com.passtheo.content.repository.StreakRepository;
import com.passtheo.content.service.AchievementService;
import com.passtheo.shared.core.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AchievementServiceTest {

    @Mock private EarnedAchievementRepository achievementRepository;
    @Mock private QuestionProgressRepository progressRepository;
    @Mock private StreakRepository streakRepository;
    @Mock private ExamAttemptRepository examAttemptRepository;
    @Mock private StudySessionRepository sessionRepository;
    @Mock private SessionAnswerRepository answerRepository;
    @Mock private ReadinessSnapshotRepository readinessSnapshotRepository;
    @Mock private LessonProgressRepository lessonProgressRepository;
    @Mock private StrapiContentCache strapiContentCache;
    @Mock private OutboxEventRepository outboxEventRepository;

    private AchievementService achievementService;

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String PRODUCT = "auto-b";

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        achievementService = new AchievementService(
                achievementRepository, progressRepository, streakRepository,
                examAttemptRepository, sessionRepository, answerRepository,
                readinessSnapshotRepository, lessonProgressRepository,
                strapiContentCache, outboxEventRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // --- QUESTIONS_ANSWERED ---

    @Test
    void questionsAnswered_achievement_earned_when_threshold_met() {
        StrapiAchievementDefDto def = achievementDef("first_question", "QUESTIONS_ANSWERED", 1);
        stubDefs(def);
        when(progressRepository.countAttempted(USER_ID, PRODUCT)).thenReturn(1);

        List<EarnedAchievementDto> result = achievementService.checkAchievements(USER_ID, PRODUCT);

        assertEquals(1, result.size());
        assertEquals("first_question", result.get(0).code());
    }

    @Test
    void questionsAnswered_not_earned_below_threshold() {
        StrapiAchievementDefDto def = achievementDef("questions_50", "QUESTIONS_ANSWERED", 50);
        stubDefs(def);
        when(progressRepository.countAttempted(USER_ID, PRODUCT)).thenReturn(25);

        List<EarnedAchievementDto> result = achievementService.checkAchievements(USER_ID, PRODUCT);

        assertTrue(result.isEmpty());
        verify(achievementRepository, never()).save(any());
    }

    // --- Duplicate prevention ---

    @Test
    void already_earned_achievement_not_awarded_again() {
        StrapiAchievementDefDto def = achievementDef("first_question", "QUESTIONS_ANSWERED", 1);
        when(strapiContentCache.getAchievements(PRODUCT)).thenReturn(List.of(def));
        when(achievementRepository.findEarnedCodes(USER_ID)).thenReturn(Set.of("first_question"));

        List<EarnedAchievementDto> result = achievementService.checkAchievements(USER_ID, PRODUCT);

        assertTrue(result.isEmpty());
        verify(achievementRepository, never()).save(any());
    }

    // --- PERFECT_SESSION / PERFECT_SESSIONS ---

    @Test
    void perfectSession_earned_when_one_perfect() {
        StrapiAchievementDefDto def = achievementDef("first_perfect", "PERFECT_SESSION", 1);
        stubDefs(def);
        when(sessionRepository.countPerfectSessions(USER_ID, PRODUCT)).thenReturn(1L);

        List<EarnedAchievementDto> result = achievementService.checkAchievements(USER_ID, PRODUCT);

        assertEquals(1, result.size());
    }

    @Test
    void perfectSessions_earned_when_ten_perfect() {
        StrapiAchievementDefDto def = achievementDef("perfect_10", "PERFECT_SESSIONS", 10);
        stubDefs(def);
        when(sessionRepository.countPerfectSessions(USER_ID, PRODUCT)).thenReturn(12L);

        List<EarnedAchievementDto> result = achievementService.checkAchievements(USER_ID, PRODUCT);

        assertEquals(1, result.size());
    }

    // --- EXAMS_COMPLETED ---

    @Test
    void examsCompleted_earned_when_threshold_met() {
        StrapiAchievementDefDto def = achievementDef("first_exam", "EXAMS_COMPLETED", 1);
        stubDefs(def);
        when(examAttemptRepository.countCompletedExams(USER_ID, PRODUCT)).thenReturn(1L);

        List<EarnedAchievementDto> result = achievementService.checkAchievements(USER_ID, PRODUCT);

        assertEquals(1, result.size());
    }

    // --- EXAMS_PASSED ---

    @Test
    void examsPassed_earned_when_threshold_met() {
        StrapiAchievementDefDto def = achievementDef("first_pass", "EXAMS_PASSED", 1);
        stubDefs(def);
        when(examAttemptRepository.countByKeycloakUserIdAndProductCodeAndPassedTrue(USER_ID, PRODUCT))
                .thenReturn(1L);

        List<EarnedAchievementDto> result = achievementService.checkAchievements(USER_ID, PRODUCT);

        assertEquals(1, result.size());
    }

    // --- CONSECUTIVE_PASSES ---

    @Test
    void consecutivePasses_earned_when_three_in_a_row() {
        StrapiAchievementDefDto def = achievementDef("pass_streak_3", "CONSECUTIVE_PASSES", 3);
        stubDefs(def);

        ExamAttempt pass1 = mockExam(true);
        ExamAttempt pass2 = mockExam(true);
        ExamAttempt pass3 = mockExam(true);
        ExamAttempt fail = mockExam(false);

        when(examAttemptRepository.findByKeycloakUserIdAndProductCodeOrderByCompletedAtDesc(
                eq(USER_ID), eq(PRODUCT), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(pass1, pass2, pass3, fail)));

        List<EarnedAchievementDto> result = achievementService.checkAchievements(USER_ID, PRODUCT);

        assertEquals(1, result.size());
    }

    @Test
    void consecutivePasses_not_earned_when_broken_by_fail() {
        StrapiAchievementDefDto def = achievementDef("pass_streak_3", "CONSECUTIVE_PASSES", 3);
        stubDefs(def);

        ExamAttempt pass1 = mockExam(true);
        ExamAttempt fail = mockExam(false);
        // pass2 comes after the fail, so the loop never reaches it
        ExamAttempt pass2 = mock(ExamAttempt.class);

        when(examAttemptRepository.findByKeycloakUserIdAndProductCodeOrderByCompletedAtDesc(
                eq(USER_ID), eq(PRODUCT), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(pass1, fail, pass2)));

        List<EarnedAchievementDto> result = achievementService.checkAchievements(USER_ID, PRODUCT);

        assertTrue(result.isEmpty());
    }

    // --- EXAM_SCORE ---

    @Test
    void examScore_earned_when_best_score_meets_threshold() {
        StrapiAchievementDefDto def = achievementDef("score_48", "EXAM_SCORE", 48);
        stubDefs(def);
        when(examAttemptRepository.findBestScore(USER_ID, PRODUCT)).thenReturn(49);

        List<EarnedAchievementDto> result = achievementService.checkAchievements(USER_ID, PRODUCT);

        assertEquals(1, result.size());
    }

    @Test
    void examScore_not_earned_when_no_exams() {
        StrapiAchievementDefDto def = achievementDef("perfect_exam", "EXAM_SCORE", 50);
        stubDefs(def);
        when(examAttemptRepository.findBestScore(USER_ID, PRODUCT)).thenReturn(null);

        List<EarnedAchievementDto> result = achievementService.checkAchievements(USER_ID, PRODUCT);

        assertTrue(result.isEmpty());
    }

    // --- STREAK_DAYS ---

    @Test
    void streakDays_earned_when_streak_meets_threshold() {
        StrapiAchievementDefDto def = achievementDef("streak_7", "STREAK_DAYS", 7);
        stubDefs(def);

        com.passtheo.content.domain.entity.Streak streak =
                new com.passtheo.content.domain.entity.Streak(USER_ID, PRODUCT);
        streak.setCurrentStreak(10);
        when(streakRepository.findByKeycloakUserIdAndProductCode(USER_ID, PRODUCT))
                .thenReturn(Optional.of(streak));

        List<EarnedAchievementDto> result = achievementService.checkAchievements(USER_ID, PRODUCT);

        assertEquals(1, result.size());
        assertEquals("streak_7", result.get(0).code());
    }

    // --- DOMAIN_MASTERED ---

    @Test
    void domainMastered_earned_when_domain_has_mastered_strength() {
        StrapiAchievementDefDto def = achievementDef("domain_master", "DOMAIN_MASTERED", 1);
        stubDefs(def);

        QuestionProgressRepository.DomainMasteryProjection agg =
                mock(QuestionProgressRepository.DomainMasteryProjection.class);
        when(agg.getDomainCode()).thenReturn("verkeersborden");
        when(agg.getAttemptedCount()).thenReturn(27L);
        when(agg.getCorrectCount()).thenReturn(27L);
        when(agg.getTotalAttempts()).thenReturn(30L);

        when(progressRepository.aggregateByDomain(USER_ID, PRODUCT)).thenReturn(List.of(agg));
        when(strapiContentCache.getQuestionCountByDomain("verkeersborden", "nl")).thenReturn(30);

        List<EarnedAchievementDto> result = achievementService.checkAchievements(USER_ID, PRODUCT);

        assertEquals(1, result.size());
        assertEquals("domain_master", result.get(0).code());
    }

    @Test
    void domainMastered_not_earned_when_low_coverage() {
        StrapiAchievementDefDto def = achievementDef("domain_master", "DOMAIN_MASTERED", 1);
        stubDefs(def);

        QuestionProgressRepository.DomainMasteryProjection agg =
                mock(QuestionProgressRepository.DomainMasteryProjection.class);
        when(agg.getDomainCode()).thenReturn("verkeersborden");
        when(agg.getAttemptedCount()).thenReturn(10L);
        when(agg.getCorrectCount()).thenReturn(7L);
        when(agg.getTotalAttempts()).thenReturn(15L);

        when(progressRepository.aggregateByDomain(USER_ID, PRODUCT)).thenReturn(List.of(agg));
        when(strapiContentCache.getQuestionCountByDomain("verkeersborden", "nl")).thenReturn(30);

        List<EarnedAchievementDto> result = achievementService.checkAchievements(USER_ID, PRODUCT);

        assertTrue(result.isEmpty());
    }

    @Test
    void domainMastered_clamps_coverage_when_questions_deactivated() {
        StrapiAchievementDefDto def = achievementDef("domain_master", "DOMAIN_MASTERED", 1);
        stubDefs(def);

        QuestionProgressRepository.DomainMasteryProjection agg =
                mock(QuestionProgressRepository.DomainMasteryProjection.class);
        when(agg.getDomainCode()).thenReturn("verkeersborden");
        when(agg.getAttemptedCount()).thenReturn(27L);
        when(agg.getCorrectCount()).thenReturn(25L);
        when(agg.getTotalAttempts()).thenReturn(30L);

        when(progressRepository.aggregateByDomain(USER_ID, PRODUCT)).thenReturn(List.of(agg));
        when(strapiContentCache.getQuestionCountByDomain("verkeersborden", "nl")).thenReturn(20);

        List<EarnedAchievementDto> result = achievementService.checkAchievements(USER_ID, PRODUCT);

        // accuracy 83.3% < 85% threshold -> NOT MASTERED
        assertTrue(result.isEmpty(), "Should not earn domain_mastered when accuracy < 85%");
    }

    // --- ALL_DOMAINS_MASTERED ---

    @Test
    void allDomainsMastered_earned_when_all_domains_mastered() {
        StrapiAchievementDefDto def = achievementDef("all_domains", "ALL_DOMAINS_MASTERED", 1);
        stubDefs(def);

        // Two domains total
        StrapiDomainDto domain1 = mock(StrapiDomainDto.class);
        StrapiDomainDto domain2 = mock(StrapiDomainDto.class);
        when(strapiContentCache.getDomains(PRODUCT, "nl")).thenReturn(List.of(domain1, domain2));

        // Both mastered: accuracy >= 85%, coverage >= 80%
        QuestionProgressRepository.DomainMasteryProjection agg1 =
                mock(QuestionProgressRepository.DomainMasteryProjection.class);
        when(agg1.getDomainCode()).thenReturn("d1");
        when(agg1.getAttemptedCount()).thenReturn(25L);
        when(agg1.getCorrectCount()).thenReturn(25L);
        when(agg1.getTotalAttempts()).thenReturn(28L);

        QuestionProgressRepository.DomainMasteryProjection agg2 =
                mock(QuestionProgressRepository.DomainMasteryProjection.class);
        when(agg2.getDomainCode()).thenReturn("d2");
        when(agg2.getAttemptedCount()).thenReturn(20L);
        when(agg2.getCorrectCount()).thenReturn(20L);
        when(agg2.getTotalAttempts()).thenReturn(22L);

        when(progressRepository.aggregateByDomain(USER_ID, PRODUCT)).thenReturn(List.of(agg1, agg2));
        when(strapiContentCache.getQuestionCountByDomain("d1", "nl")).thenReturn(28);
        when(strapiContentCache.getQuestionCountByDomain("d2", "nl")).thenReturn(22);

        List<EarnedAchievementDto> result = achievementService.checkAchievements(USER_ID, PRODUCT);

        assertEquals(1, result.size());
    }

    @Test
    void allDomainsMastered_not_earned_when_one_domain_not_mastered() {
        StrapiAchievementDefDto def = achievementDef("all_domains", "ALL_DOMAINS_MASTERED", 1);
        stubDefs(def);

        StrapiDomainDto domain1 = mock(StrapiDomainDto.class);
        StrapiDomainDto domain2 = mock(StrapiDomainDto.class);
        when(strapiContentCache.getDomains(PRODUCT, "nl")).thenReturn(List.of(domain1, domain2));

        // Only one mastered
        QuestionProgressRepository.DomainMasteryProjection agg1 =
                mock(QuestionProgressRepository.DomainMasteryProjection.class);
        when(agg1.getDomainCode()).thenReturn("d1");
        when(agg1.getAttemptedCount()).thenReturn(25L);
        when(agg1.getCorrectCount()).thenReturn(25L);
        when(agg1.getTotalAttempts()).thenReturn(28L);

        when(progressRepository.aggregateByDomain(USER_ID, PRODUCT)).thenReturn(List.of(agg1));
        when(strapiContentCache.getQuestionCountByDomain("d1", "nl")).thenReturn(28);

        List<EarnedAchievementDto> result = achievementService.checkAchievements(USER_ID, PRODUCT);

        assertTrue(result.isEmpty());
    }

    // --- READINESS_SCORE ---

    @Test
    void readinessScore_earned_when_snapshot_meets_threshold() {
        StrapiAchievementDefDto def = achievementDef("readiness_80", "READINESS_SCORE", 80);
        stubDefs(def);

        ReadinessSnapshot snapshot = new ReadinessSnapshot();
        snapshot.setReadinessScore(BigDecimal.valueOf(85.5));
        when(readinessSnapshotRepository.findTopByKeycloakUserIdAndProductCodeOrderBySnapshotDateDesc(
                USER_ID, PRODUCT)).thenReturn(Optional.of(snapshot));

        List<EarnedAchievementDto> result = achievementService.checkAchievements(USER_ID, PRODUCT);

        assertEquals(1, result.size());
    }

    @Test
    void readinessScore_not_earned_when_no_snapshot() {
        StrapiAchievementDefDto def = achievementDef("readiness_80", "READINESS_SCORE", 80);
        stubDefs(def);

        when(readinessSnapshotRepository.findTopByKeycloakUserIdAndProductCodeOrderBySnapshotDateDesc(
                USER_ID, PRODUCT)).thenReturn(Optional.empty());

        List<EarnedAchievementDto> result = achievementService.checkAchievements(USER_ID, PRODUCT);

        assertTrue(result.isEmpty());
    }

    // --- AVG_ANSWER_TIME_BELOW ---

    @Test
    void avgAnswerTimeBelow_earned_when_fast_enough() {
        StrapiAchievementDefDto def = achievementDef("speed_demon", "AVG_ANSWER_TIME_BELOW", 8);
        stubDefs(def);
        when(answerRepository.averageTimeTakenMs(USER_ID, PRODUCT)).thenReturn(6500.0); // 6.5 seconds

        List<EarnedAchievementDto> result = achievementService.checkAchievements(USER_ID, PRODUCT);

        assertEquals(1, result.size());
    }

    @Test
    void avgAnswerTimeBelow_not_earned_when_too_slow() {
        StrapiAchievementDefDto def = achievementDef("speed_demon", "AVG_ANSWER_TIME_BELOW", 8);
        stubDefs(def);
        when(answerRepository.averageTimeTakenMs(USER_ID, PRODUCT)).thenReturn(12000.0); // 12 seconds

        List<EarnedAchievementDto> result = achievementService.checkAchievements(USER_ID, PRODUCT);

        assertTrue(result.isEmpty());
    }

    @Test
    void avgAnswerTimeBelow_not_earned_when_no_answers() {
        StrapiAchievementDefDto def = achievementDef("speed_demon", "AVG_ANSWER_TIME_BELOW", 8);
        stubDefs(def);
        when(answerRepository.averageTimeTakenMs(USER_ID, PRODUCT)).thenReturn(null);

        List<EarnedAchievementDto> result = achievementService.checkAchievements(USER_ID, PRODUCT);

        assertTrue(result.isEmpty());
    }

    // --- isThresholdMet ---

    @Test
    void isThresholdMet_standard_uses_greater_equal() {
        assertTrue(AchievementService.isThresholdMet("QUESTIONS_ANSWERED", 10, 10));
        assertTrue(AchievementService.isThresholdMet("QUESTIONS_ANSWERED", 15, 10));
        assertFalse(AchievementService.isThresholdMet("QUESTIONS_ANSWERED", 9, 10));
    }

    @Test
    void isThresholdMet_avgTimeBelow_uses_less_equal() {
        // 6 seconds avg, threshold 8 -> earned
        assertTrue(AchievementService.isThresholdMet("AVG_ANSWER_TIME_BELOW", 6, 8));
        // 8 seconds avg, threshold 8 -> earned (equal counts)
        assertTrue(AchievementService.isThresholdMet("AVG_ANSWER_TIME_BELOW", 8, 8));
        // 10 seconds avg, threshold 8 -> NOT earned
        assertFalse(AchievementService.isThresholdMet("AVG_ANSWER_TIME_BELOW", 10, 8));
        // 0 (no data), threshold 8 -> NOT earned
        assertFalse(AchievementService.isThresholdMet("AVG_ANSWER_TIME_BELOW", 0, 8));
    }

    // --- Unknown trigger type ---

    @Test
    void unknown_trigger_type_returns_zero_does_not_earn() {
        StrapiAchievementDefDto def = new StrapiAchievementDefDto(
                1, null, "Unknown", "unknown_ach", "desc", "?", "?",
                "UNKNOWN_TRIGGER_XYZ", 1, 0, true, 1, null);
        stubDefs(def);

        List<EarnedAchievementDto> result = achievementService.checkAchievements(USER_ID, PRODUCT);

        assertTrue(result.isEmpty());
    }

    // --- Tenant isolation ---

    @Test
    void saved_achievement_has_correct_tenant_id() {
        StrapiAchievementDefDto def = achievementDef("first_question", "QUESTIONS_ANSWERED", 1);
        stubDefs(def);
        when(progressRepository.countAttempted(USER_ID, PRODUCT)).thenReturn(1);
        ArgumentCaptor<EarnedAchievement> captor = ArgumentCaptor.forClass(EarnedAchievement.class);
        when(achievementRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        achievementService.checkAchievements(USER_ID, PRODUCT);

        assertEquals(TENANT_ID, captor.getValue().getTenantId());
    }

    // --- Helpers ---

    private StrapiAchievementDefDto achievementDef(String code, String triggerType, int triggerValue) {
        return new StrapiAchievementDefDto(
                1, null, "Achievement " + code, code, "desc", "icon", "locked",
                triggerType, triggerValue, 50, true, 1, null);
    }

    private void stubDefs(StrapiAchievementDefDto... defs) {
        when(strapiContentCache.getAchievements(PRODUCT)).thenReturn(List.of(defs));
        when(achievementRepository.findEarnedCodes(USER_ID)).thenReturn(Set.of());
        org.mockito.Mockito.lenient().when(achievementRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private ExamAttempt mockExam(boolean passed) {
        ExamAttempt exam = mock(ExamAttempt.class);
        when(exam.isPassed()).thenReturn(passed);
        return exam;
    }
}
