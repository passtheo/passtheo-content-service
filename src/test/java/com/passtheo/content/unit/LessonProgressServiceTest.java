package com.passtheo.content.unit;

import com.passtheo.content.domain.entity.LessonProgress;
import com.passtheo.content.domain.valueobject.XpResult;
import com.passtheo.content.dto.response.EarnedAchievementDto;
import com.passtheo.content.dto.response.LessonCompleteResponse;
import com.passtheo.content.dto.response.LessonProgressDto;
import com.passtheo.content.integration.strapi.StrapiContentCache;
import com.passtheo.content.integration.strapi.dto.StrapiLessonDto;
import com.passtheo.content.repository.LessonProgressRepository;
import com.passtheo.content.service.AchievementService;
import com.passtheo.content.service.LessonProgressService;
import com.passtheo.content.service.XpService;
import com.passtheo.shared.core.context.TenantContext;
import com.passtheo.shared.core.exception.AppException;
import com.passtheo.shared.outbox.entity.OutboxEvent;
import com.passtheo.shared.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LessonProgressServiceTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String PRODUCT = "auto-b";
    private static final String TOPIC = "verbodsborden";
    private static final String SLUG = "voorrangsbord-uitleg";

    @Mock private LessonProgressRepository lessonProgressRepository;
    @Mock private XpService xpService;
    @Mock private AchievementService achievementService;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private StrapiContentCache strapiContentCache;

    private LessonProgressService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);
        com.fasterxml.jackson.databind.ObjectMapper realMapper =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        service = new LessonProgressService(
                lessonProgressRepository, xpService, achievementService, outboxEventRepository,
                strapiContentCache, realMapper);

        // Default: every lesson slug is valid. Tests that need to assert invalid
        // slugs override this stub. Lenient because some tests (e.g., getProgress
        // queries) don't exercise the validation path.
        lenient().when(strapiContentCache.getLessons(any(), any()))
                .thenReturn(List.of(
                        new StrapiLessonDto(1, "doc-1", "Voorrangsbord uitleg", SLUG,
                                List.of(), null, null, null, 0, true, false, 0)));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void completeLesson_unknownSlug_throws404() {
        when(strapiContentCache.getLessons(TOPIC, "nl")).thenReturn(List.of());

        assertThatThrownBy(() ->
                service.completeLesson(USER_ID, "fake-slug", PRODUCT, TOPIC, 60))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Lesson not found");

        verifyNoInteractions(lessonProgressRepository, xpService, achievementService, outboxEventRepository);
    }

    @Test
    void completeLesson_firstCompletion_grantsXpAndPublishesEvent() {
        when(lessonProgressRepository.findByKeycloakUserIdAndProductCodeAndLessonSlug(USER_ID, PRODUCT, SLUG))
                .thenReturn(Optional.empty());
        when(achievementService.checkAchievements(USER_ID, PRODUCT)).thenReturn(List.of());
        when(xpService.grantXp(eq(USER_ID), eq(PRODUCT), anyInt()))
                .thenReturn(new XpResult(20, 20, 1, 100, false, 1));

        LessonCompleteResponse response = service.completeLesson(USER_ID, SLUG, PRODUCT, TOPIC, 180);

        assertThat(response.lessonSlug()).isEqualTo(SLUG);
        assertThat(response.isCompleted()).isTrue();
        assertThat(response.completedAt()).isNotNull();
        assertThat(response.xpUpdate().xpEarned()).isEqualTo(20);
        assertThat(response.xpUpdate().totalXp()).isEqualTo(20);
        assertThat(response.newAchievements()).isEmpty();

        verify(xpService).grantXp(USER_ID, PRODUCT, XpService.XP_LESSON_COMPLETE);

        ArgumentCaptor<LessonProgress> savedRow = ArgumentCaptor.forClass(LessonProgress.class);
        verify(lessonProgressRepository).save(savedRow.capture());
        assertThat(savedRow.getValue().isCompleted()).isTrue();
        assertThat(savedRow.getValue().getTimeSpentSeconds()).isEqualTo(180);

        ArgumentCaptor<OutboxEvent> outbox = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outbox.capture());
        assertThat(outbox.getValue().getEventType()).isEqualTo("LessonCompleted");
        assertThat(outbox.getValue().getPartitionKey()).isEqualTo(USER_ID.toString());
    }

    @Test
    void completeLesson_firstCompletion_addsAchievementXpToGrant() {
        when(lessonProgressRepository.findByKeycloakUserIdAndProductCodeAndLessonSlug(USER_ID, PRODUCT, SLUG))
                .thenReturn(Optional.empty());
        when(achievementService.checkAchievements(USER_ID, PRODUCT)).thenReturn(List.of(
                new EarnedAchievementDto("first_lesson", "First lesson", "icon", 10)));
        when(xpService.grantXp(eq(USER_ID), eq(PRODUCT), anyInt()))
                .thenReturn(new XpResult(30, 30, 1, 100, false, 1));

        LessonCompleteResponse response = service.completeLesson(USER_ID, SLUG, PRODUCT, TOPIC, 120);

        // XP_LESSON_COMPLETE (20) + achievement xpReward (10) = 30
        verify(xpService).grantXp(USER_ID, PRODUCT, XpService.XP_LESSON_COMPLETE + 10);
        assertThat(response.xpUpdate().xpEarned()).isEqualTo(30);
        assertThat(response.newAchievements()).hasSize(1);
        assertThat(response.newAchievements().get(0).code()).isEqualTo("first_lesson");
    }

    @Test
    void completeLesson_alreadyCompleted_isIdempotentWithZeroXp() {
        LessonProgress existing = new LessonProgress(USER_ID, PRODUCT, TOPIC, SLUG);
        existing.setCompleted(true);
        existing.setCompletedAt(Instant.parse("2026-04-01T10:00:00Z"));
        existing.setTimeSpentSeconds(300);

        when(lessonProgressRepository.findByKeycloakUserIdAndProductCodeAndLessonSlug(USER_ID, PRODUCT, SLUG))
                .thenReturn(Optional.of(existing));
        when(xpService.getXp(USER_ID, PRODUCT))
                .thenReturn(new XpResult(0, 500, 3, 600, false, 3));

        LessonCompleteResponse response = service.completeLesson(USER_ID, SLUG, PRODUCT, TOPIC, 999);

        assertThat(response.isCompleted()).isTrue();
        assertThat(response.xpUpdate().xpEarned()).isZero();
        assertThat(response.xpUpdate().totalXp()).isEqualTo(500);
        assertThat(response.newAchievements()).isEmpty();
        assertThat(response.completedAt()).isEqualTo(Instant.parse("2026-04-01T10:00:00Z"));

        verify(xpService, never()).grantXp(any(), any(), anyInt());
        verifyNoInteractions(achievementService, outboxEventRepository);
        verify(lessonProgressRepository, never()).save(any());
    }

    @Test
    void completeLesson_existingIncompleteRow_isMarkedComplete() {
        LessonProgress existing = new LessonProgress(USER_ID, PRODUCT, TOPIC, SLUG);
        existing.setTimeSpentSeconds(60);

        when(lessonProgressRepository.findByKeycloakUserIdAndProductCodeAndLessonSlug(USER_ID, PRODUCT, SLUG))
                .thenReturn(Optional.of(existing));
        when(achievementService.checkAchievements(USER_ID, PRODUCT)).thenReturn(List.of());
        when(xpService.grantXp(eq(USER_ID), eq(PRODUCT), anyInt()))
                .thenReturn(new XpResult(20, 80, 1, 100, false, 1));

        service.completeLesson(USER_ID, SLUG, PRODUCT, TOPIC, 45);

        ArgumentCaptor<LessonProgress> saved = ArgumentCaptor.forClass(LessonProgress.class);
        verify(lessonProgressRepository).save(saved.capture());
        assertThat(saved.getValue().isCompleted()).isTrue();
        // Existing 60 + new 45 = 105
        assertThat(saved.getValue().getTimeSpentSeconds()).isEqualTo(105);
    }

    @Test
    void uncompleteLesson_clearsCompletionFlagWithoutRefund() {
        LessonProgress existing = new LessonProgress(USER_ID, PRODUCT, TOPIC, SLUG);
        existing.setCompleted(true);
        existing.setCompletedAt(Instant.now());
        existing.setTimeSpentSeconds(240);

        when(lessonProgressRepository.findByKeycloakUserIdAndProductCodeAndLessonSlug(USER_ID, PRODUCT, SLUG))
                .thenReturn(Optional.of(existing));

        service.uncompleteLesson(USER_ID, SLUG, PRODUCT);

        ArgumentCaptor<LessonProgress> saved = ArgumentCaptor.forClass(LessonProgress.class);
        verify(lessonProgressRepository).save(saved.capture());
        assertThat(saved.getValue().isCompleted()).isFalse();
        // time_spent_seconds preserved
        assertThat(saved.getValue().getTimeSpentSeconds()).isEqualTo(240);
        // completed_at preserved (not reset to null)
        assertThat(saved.getValue().getCompletedAt()).isNotNull();

        verifyNoInteractions(xpService, achievementService, outboxEventRepository);
    }

    @Test
    void uncompleteLesson_missingRow_isNoop() {
        when(lessonProgressRepository.findByKeycloakUserIdAndProductCodeAndLessonSlug(USER_ID, PRODUCT, SLUG))
                .thenReturn(Optional.empty());

        service.uncompleteLesson(USER_ID, SLUG, PRODUCT);

        verify(lessonProgressRepository, never()).save(any());
        verifyNoInteractions(xpService, achievementService, outboxEventRepository);
    }

    @Test
    void getProgressForTopic_returnsAllLessonsInTopic() {
        LessonProgress a = new LessonProgress(USER_ID, PRODUCT, TOPIC, "lesson-a");
        a.setCompleted(true);
        a.setCompletedAt(Instant.parse("2026-03-01T09:00:00Z"));
        a.setTimeSpentSeconds(120);
        LessonProgress b = new LessonProgress(USER_ID, PRODUCT, TOPIC, "lesson-b");
        b.setTimeSpentSeconds(60);

        when(lessonProgressRepository
                .findByKeycloakUserIdAndProductCodeAndTopicCode(USER_ID, PRODUCT, TOPIC))
                .thenReturn(List.of(a, b));

        List<LessonProgressDto> result = service.getProgressForTopic(USER_ID, PRODUCT, TOPIC);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).lessonSlug()).isEqualTo("lesson-a");
        assertThat(result.get(0).isCompleted()).isTrue();
        assertThat(result.get(0).timeSpentSeconds()).isEqualTo(120);
        assertThat(result.get(1).lessonSlug()).isEqualTo("lesson-b");
        assertThat(result.get(1).isCompleted()).isFalse();
    }
}
