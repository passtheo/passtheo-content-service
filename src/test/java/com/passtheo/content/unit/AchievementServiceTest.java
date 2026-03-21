package com.passtheo.content.unit;

import com.passtheo.content.domain.entity.EarnedAchievement;
import com.passtheo.content.domain.enums.DomainStrength;
import com.passtheo.content.dto.response.EarnedAchievementDto;
import com.passtheo.content.integration.strapi.StrapiContentCache;
import com.passtheo.content.integration.strapi.dto.StrapiAchievementDefDto;
import com.passtheo.content.repository.DomainProgressRepository;
import com.passtheo.content.repository.EarnedAchievementRepository;
import com.passtheo.content.repository.ExamAttemptRepository;
import com.passtheo.content.repository.OutboxEventRepository;
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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AchievementServiceTest {

    @Mock private EarnedAchievementRepository achievementRepository;
    @Mock private QuestionProgressRepository progressRepository;
    @Mock private StreakRepository streakRepository;
    @Mock private ExamAttemptRepository examAttemptRepository;
    @Mock private DomainProgressRepository domainProgressRepository;
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
                examAttemptRepository, domainProgressRepository, strapiContentCache, outboxEventRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void firstQuestion_achievement_earned_when_threshold_met() {
        StrapiAchievementDefDto def = new StrapiAchievementDefDto(
                "Eerste Stap", "first_question", "desc", "🎯", "🔒",
                "questions_answered", 1, 10, true, 1, null);
        when(strapiContentCache.getAchievements(PRODUCT)).thenReturn(List.of(def));
        when(achievementRepository.findEarnedCodes(USER_ID)).thenReturn(Set.of());
        when(progressRepository.countAttempted(USER_ID, PRODUCT)).thenReturn(1);
        when(achievementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<EarnedAchievementDto> result = achievementService.checkAchievements(USER_ID, PRODUCT);

        assertEquals(1, result.size());
        assertEquals("first_question", result.get(0).code());
        assertEquals("Eerste Stap", result.get(0).name());
    }

    @Test
    void already_earned_achievement_not_awarded_again() {
        StrapiAchievementDefDto def = new StrapiAchievementDefDto(
                "Eerste Stap", "first_question", "desc", "🎯", "🔒",
                "questions_answered", 1, 10, true, 1, null);
        when(strapiContentCache.getAchievements(PRODUCT)).thenReturn(List.of(def));
        when(achievementRepository.findEarnedCodes(USER_ID)).thenReturn(Set.of("first_question"));

        List<EarnedAchievementDto> result = achievementService.checkAchievements(USER_ID, PRODUCT);

        assertTrue(result.isEmpty());
        verify(achievementRepository, never()).save(any());
    }

    @Test
    void streak_achievement_uses_streak_repository() {
        StrapiAchievementDefDto def = new StrapiAchievementDefDto(
                "Weekkampioen", "7_day_streak", "desc", "🔥", "🔒",
                "study_days_streak", 7, 100, true, 1, null);
        when(strapiContentCache.getAchievements(PRODUCT)).thenReturn(List.of(def));
        when(achievementRepository.findEarnedCodes(USER_ID)).thenReturn(Set.of());

        com.passtheo.content.domain.entity.Streak streak =
                new com.passtheo.content.domain.entity.Streak(USER_ID, PRODUCT);
        streak.setCurrentStreak(10);
        when(streakRepository.findByKeycloakUserIdAndProductCode(USER_ID, PRODUCT))
                .thenReturn(Optional.of(streak));
        when(achievementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<EarnedAchievementDto> result = achievementService.checkAchievements(USER_ID, PRODUCT);

        assertEquals(1, result.size());
        assertEquals("7_day_streak", result.get(0).code());
    }

    @Test
    void exam_passed_achievement_uses_exam_repository() {
        StrapiAchievementDefDto def = new StrapiAchievementDefDto(
                "Geslaagd", "first_pass", "desc", "🏆", "🔒",
                "exams_passed", 1, 50, true, 1, null);
        when(strapiContentCache.getAchievements(PRODUCT)).thenReturn(List.of(def));
        when(achievementRepository.findEarnedCodes(USER_ID)).thenReturn(Set.of());
        when(examAttemptRepository.countByKeycloakUserIdAndProductCodeAndPassedTrue(USER_ID, PRODUCT))
                .thenReturn(1L);
        when(achievementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<EarnedAchievementDto> result = achievementService.checkAchievements(USER_ID, PRODUCT);

        assertEquals(1, result.size());
    }

    @Test
    void perfect_exam_achievement_uses_count_perfect() {
        StrapiAchievementDefDto def = new StrapiAchievementDefDto(
                "Perfectionist", "perfect_exam", "desc", "💯", "🔒",
                "perfect_exam", 1, 100, true, 1, null);
        when(strapiContentCache.getAchievements(PRODUCT)).thenReturn(List.of(def));
        when(achievementRepository.findEarnedCodes(USER_ID)).thenReturn(Set.of());
        when(examAttemptRepository.countPerfect(USER_ID, PRODUCT)).thenReturn(1L);
        when(achievementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<EarnedAchievementDto> result = achievementService.checkAchievements(USER_ID, PRODUCT);

        assertEquals(1, result.size());
    }

    @Test
    void domain_mastered_achievement_uses_domain_progress_repository() {
        StrapiAchievementDefDto def = new StrapiAchievementDefDto(
                "Domeinmeester", "domain_mastered", "desc", "🎓", "🔒",
                "domain_mastered", 1, 75, true, 1, null);
        when(strapiContentCache.getAchievements(PRODUCT)).thenReturn(List.of(def));
        when(achievementRepository.findEarnedCodes(USER_ID)).thenReturn(Set.of());
        when(domainProgressRepository.countByKeycloakUserIdAndProductCodeAndStrength(
                USER_ID, PRODUCT, DomainStrength.MASTERED)).thenReturn(2L);
        when(achievementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<EarnedAchievementDto> result = achievementService.checkAchievements(USER_ID, PRODUCT);

        assertEquals(1, result.size());
    }

    @Test
    void below_threshold_achievement_not_earned() {
        StrapiAchievementDefDto def = new StrapiAchievementDefDto(
                "Beginnersluk", "ten_questions", "desc", "⭐", "🔒",
                "questions_answered", 10, 50, true, 1, null);
        when(strapiContentCache.getAchievements(PRODUCT)).thenReturn(List.of(def));
        when(achievementRepository.findEarnedCodes(USER_ID)).thenReturn(Set.of());
        when(progressRepository.countAttempted(USER_ID, PRODUCT)).thenReturn(5);

        List<EarnedAchievementDto> result = achievementService.checkAchievements(USER_ID, PRODUCT);

        assertTrue(result.isEmpty());
        verify(achievementRepository, never()).save(any());
    }

    @Test
    void unknown_trigger_type_returns_zero_does_not_earn() {
        StrapiAchievementDefDto def = new StrapiAchievementDefDto(
                "Unknown", "unknown_ach", "desc", "?", "?",
                "unknown_trigger_xyz", 1, 0, true, 1, null);
        when(strapiContentCache.getAchievements(PRODUCT)).thenReturn(List.of(def));
        when(achievementRepository.findEarnedCodes(USER_ID)).thenReturn(Set.of());

        List<EarnedAchievementDto> result = achievementService.checkAchievements(USER_ID, PRODUCT);

        assertTrue(result.isEmpty());
    }

    @Test
    void saved_achievement_has_correct_tenant_id() {
        StrapiAchievementDefDto def = new StrapiAchievementDefDto(
                "Eerste Stap", "first_question", "desc", "🎯", "🔒",
                "questions_answered", 1, 10, true, 1, null);
        when(strapiContentCache.getAchievements(PRODUCT)).thenReturn(List.of(def));
        when(achievementRepository.findEarnedCodes(USER_ID)).thenReturn(Set.of());
        when(progressRepository.countAttempted(USER_ID, PRODUCT)).thenReturn(1);
        ArgumentCaptor<EarnedAchievement> captor = ArgumentCaptor.forClass(EarnedAchievement.class);
        when(achievementRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        achievementService.checkAchievements(USER_ID, PRODUCT);

        assertEquals(TENANT_ID, captor.getValue().getTenantId());
    }
}
