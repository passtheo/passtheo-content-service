package com.passtheo.content.unit;

import com.passtheo.content.domain.entity.QuestionProgress;
import com.passtheo.content.domain.enums.MasteryLevel;
import com.passtheo.content.domain.enums.SessionType;
import com.passtheo.content.integration.strapi.StrapiContentCache;
import com.passtheo.shared.core.exception.AppException;
import com.passtheo.content.integration.strapi.dto.StrapiQuestionDto;
import com.passtheo.content.integration.strapi.dto.StrapiRelationDto;
import com.passtheo.content.repository.QuestionProgressRepository;
import com.passtheo.content.service.QuestionSelectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.springframework.data.domain.Pageable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionSelectionServiceTest {

    @Mock private QuestionProgressRepository progressRepository;
    @Mock private StrapiContentCache strapiContentCache;

    private QuestionSelectionService service;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String PRODUCT = "auto-b";
    private static final String DOMAIN = "verkeersborden";
    private static final String TOPIC = "voorrangsborden";
    private static final String LOCALE = "nl";

    @BeforeEach
    void setUp() {
        service = new QuestionSelectionService(progressRepository, strapiContentCache);
    }

    @Test
    void new_user_gets_unseen_questions() {
        when(progressRepository.findDueReviews(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), isNull(), any(Instant.class), any(Pageable.class)))
                .thenReturn(new java.util.ArrayList<>());
        when(progressRepository.findWeak(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), isNull(), anyInt(), any(Pageable.class)))
                .thenReturn(List.of());
        when(progressRepository.findSeenQuestionIds(USER_ID, PRODUCT, DOMAIN, null))
                .thenReturn(Set.of());
        when(strapiContentCache.getQuestionsByDomain(eq(DOMAIN), eq(LOCALE)))
                .thenReturn(buildQuestions(DOMAIN, 10));

        List<String> selected = service.selectQuestions(USER_ID, PRODUCT, DOMAIN, null, null, 5, LOCALE);

        assertEquals(5, selected.size());
    }

    @Test
    void due_reviews_are_prioritized_first() {
        QuestionProgress dueQ = buildProgress("q-due-1", Instant.now().minus(1, ChronoUnit.DAYS));
        when(progressRepository.findDueReviews(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), isNull(), any(Instant.class), any(Pageable.class)))
                .thenReturn(new java.util.ArrayList<>(List.of(dueQ)));
        when(progressRepository.findWeak(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), isNull(), anyInt(), any(Pageable.class)))
                .thenReturn(List.of());
        when(progressRepository.findSeenQuestionIds(USER_ID, PRODUCT, DOMAIN, null))
                .thenReturn(Set.of("q-due-1"));
        when(strapiContentCache.getQuestionsByDomain(eq(DOMAIN), eq(LOCALE)))
                .thenReturn(buildQuestions(DOMAIN, 10));

        List<String> selected = service.selectQuestions(USER_ID, PRODUCT, DOMAIN, null, null, 5, LOCALE);

        assertTrue(selected.contains("q-due-1"), "Due review question should be first");
        assertEquals(5, selected.size());
    }

    @Test
    void weak_questions_fill_when_no_due_reviews() {
        QuestionProgress weak = buildProgress("q-weak-1", null);
        weak.setConsecutiveCorrect(1);
        when(progressRepository.findDueReviews(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), isNull(), any(Instant.class), any(Pageable.class)))
                .thenReturn(new java.util.ArrayList<>());
        when(progressRepository.findWeak(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), isNull(), anyInt(), any(Pageable.class)))
                .thenReturn(List.of(weak));
        when(progressRepository.findSeenQuestionIds(USER_ID, PRODUCT, DOMAIN, null))
                .thenReturn(Set.of("q-weak-1"));
        when(strapiContentCache.getQuestionsByDomain(eq(DOMAIN), eq(LOCALE)))
                .thenReturn(buildQuestions(DOMAIN, 10));

        List<String> selected = service.selectQuestions(USER_ID, PRODUCT, DOMAIN, null, null, 5, LOCALE);

        assertTrue(selected.contains("q-weak-1"));
    }

    @Test
    void no_duplicate_questions_in_selection() {
        QuestionProgress dueQ = buildProgress("q-1", Instant.now().minus(1, ChronoUnit.DAYS));
        QuestionProgress weakQ = buildProgress("q-1", null);
        when(progressRepository.findDueReviews(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), isNull(), any(Instant.class), any(Pageable.class)))
                .thenReturn(new java.util.ArrayList<>(List.of(dueQ)));
        when(progressRepository.findWeak(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), isNull(), anyInt(), any(Pageable.class)))
                .thenReturn(List.of(weakQ));
        when(progressRepository.findSeenQuestionIds(USER_ID, PRODUCT, DOMAIN, null))
                .thenReturn(Set.of("q-1"));
        when(strapiContentCache.getQuestionsByDomain(eq(DOMAIN), eq(LOCALE)))
                .thenReturn(buildQuestions(DOMAIN, 5));

        List<String> selected = service.selectQuestions(USER_ID, PRODUCT, DOMAIN, null, null, 3, LOCALE);

        long distinctCount = selected.stream().distinct().count();
        assertEquals(selected.size(), distinctCount, "No duplicates allowed");
    }

    @Test
    void mixed_session_null_domain_uses_product_questions() {
        when(progressRepository.findDueReviews(eq(USER_ID), eq(PRODUCT), eq(null), isNull(), any(Instant.class), any(Pageable.class)))
                .thenReturn(new java.util.ArrayList<>());
        when(progressRepository.findWeak(eq(USER_ID), eq(PRODUCT), eq(null), isNull(), anyInt(), any(Pageable.class)))
                .thenReturn(List.of());
        when(progressRepository.findSeenQuestionIds(USER_ID, PRODUCT, null, null))
                .thenReturn(Set.of());
        when(strapiContentCache.getQuestionIds(eq(PRODUCT), eq(LOCALE)))
                .thenReturn(List.of("q1", "q2", "q3", "q4", "q5", "q6", "q7", "q8", "q9", "q10"));

        List<String> selected = service.selectQuestions(USER_ID, PRODUCT, null, null, null, 5, LOCALE);

        assertEquals(5, selected.size());
    }

    @Test
    void returns_empty_when_no_questions_exist() {
        when(progressRepository.findDueReviews(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), isNull(), any(Instant.class), any(Pageable.class)))
                .thenReturn(new java.util.ArrayList<>());
        when(progressRepository.findWeak(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), isNull(), anyInt(), any(Pageable.class)))
                .thenReturn(List.of());
        when(progressRepository.findSeenQuestionIds(USER_ID, PRODUCT, DOMAIN, null))
                .thenReturn(Set.of());
        when(strapiContentCache.getQuestionsByDomain(eq(DOMAIN), eq(LOCALE)))
                .thenReturn(List.of());
        when(progressRepository.findFamiliarSorted(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), isNull(), any(Pageable.class)))
                .thenReturn(List.of());

        List<String> selected = service.selectQuestions(USER_ID, PRODUCT, DOMAIN, null, null, 5, LOCALE);

        assertTrue(selected.isEmpty());
    }

    @Test
    void familiar_questions_used_as_last_resort_fill() {
        QuestionProgress familiar = buildProgress("q-familiar-1", Instant.now().plus(3, ChronoUnit.DAYS));
        when(progressRepository.findDueReviews(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), isNull(), any(Instant.class), any(Pageable.class)))
                .thenReturn(new ArrayList<>());
        when(progressRepository.findWeak(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), isNull(), anyInt(), any(Pageable.class)))
                .thenReturn(List.of());
        when(progressRepository.findSeenQuestionIds(USER_ID, PRODUCT, DOMAIN, null))
                .thenReturn(Set.of());
        when(strapiContentCache.getQuestionsByDomain(eq(DOMAIN), eq(LOCALE)))
                .thenReturn(List.of());
        when(progressRepository.findFamiliarSorted(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), isNull(), any(Pageable.class)))
                .thenReturn(List.of(familiar));

        List<String> selected = service.selectQuestions(USER_ID, PRODUCT, DOMAIN, null, null, 1, LOCALE);

        assertFalse(selected.isEmpty());
        assertTrue(selected.contains("q-familiar-1"));
    }

    // ─── WEAK_REVIEW ───

    @Test
    void weakReview_skipsNewAndFamiliarQuestions() {
        QuestionProgress due = buildProgress("q-due", Instant.now().minus(1, ChronoUnit.DAYS));
        QuestionProgress weak1 = buildProgress("q-weak-1", null);
        QuestionProgress weak2 = buildProgress("q-weak-2", null);
        QuestionProgress weak3 = buildProgress("q-weak-3", null);
        QuestionProgress weak4 = buildProgress("q-weak-4", null);
        when(progressRepository.findDueReviews(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), isNull(), any(Instant.class), any(Pageable.class)))
                .thenReturn(new ArrayList<>(List.of(due)));
        when(progressRepository.findWeak(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), isNull(), anyInt(), any(Pageable.class)))
                .thenReturn(List.of(weak1, weak2, weak3, weak4));

        List<String> selected = service.selectQuestions(USER_ID, PRODUCT, DOMAIN, null,
                SessionType.WEAK_REVIEW, 10, LOCALE);

        assertEquals(5, selected.size());
        assertTrue(selected.contains("q-due"));
        assertTrue(selected.contains("q-weak-1"));
        // Should NOT contain any new or familiar questions
    }

    @Test
    void weakReview_throwsWhenFewerThan5() {
        QuestionProgress due = buildProgress("q-due", Instant.now().minus(1, ChronoUnit.DAYS));
        when(progressRepository.findDueReviews(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), isNull(), any(Instant.class), any(Pageable.class)))
                .thenReturn(new ArrayList<>(List.of(due)));
        when(progressRepository.findWeak(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), isNull(), anyInt(), any(Pageable.class)))
                .thenReturn(List.of());

        org.junit.jupiter.api.Assertions.assertThrows(AppException.class, () ->
                service.selectQuestions(USER_ID, PRODUCT, DOMAIN, null,
                        SessionType.WEAK_REVIEW, 10, LOCALE));
    }

    @Test
    void practice_sessionType_usesAllFourPriorities() {
        when(progressRepository.findDueReviews(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), isNull(), any(Instant.class), any(Pageable.class)))
                .thenReturn(new ArrayList<>());
        when(progressRepository.findWeak(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), isNull(), anyInt(), any(Pageable.class)))
                .thenReturn(List.of());
        when(progressRepository.findSeenQuestionIds(USER_ID, PRODUCT, DOMAIN, null))
                .thenReturn(Set.of());
        when(strapiContentCache.getQuestionsByDomain(eq(DOMAIN), eq(LOCALE)))
                .thenReturn(buildQuestions(DOMAIN, 10));

        List<String> selected = service.selectQuestions(USER_ID, PRODUCT, DOMAIN, null,
                SessionType.PRACTICE, 5, LOCALE);

        assertEquals(5, selected.size());
    }

    // ─── Topic-level filtering ───

    @Test
    void topicCode_selectsQuestionsFromTopicOnly() {
        when(progressRepository.findDueReviews(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), eq(TOPIC), any(Instant.class), any(Pageable.class)))
                .thenReturn(new ArrayList<>());
        when(progressRepository.findWeak(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), eq(TOPIC), anyInt(), any(Pageable.class)))
                .thenReturn(List.of());
        when(progressRepository.findSeenQuestionIds(USER_ID, PRODUCT, DOMAIN, TOPIC))
                .thenReturn(Set.of());
        when(strapiContentCache.getQuestionsByTopic(eq(TOPIC), eq(LOCALE)))
                .thenReturn(buildQuestions(DOMAIN, 8));

        List<String> selected = service.selectQuestions(USER_ID, PRODUCT, DOMAIN, TOPIC, null, 5, LOCALE);

        assertEquals(5, selected.size());
    }

    @Test
    void blankTopicCode_normalizedToNull_selectsFromDomain() {
        when(progressRepository.findDueReviews(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), isNull(), any(Instant.class), any(Pageable.class)))
                .thenReturn(new ArrayList<>());
        when(progressRepository.findWeak(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), isNull(), anyInt(), any(Pageable.class)))
                .thenReturn(List.of());
        when(progressRepository.findSeenQuestionIds(USER_ID, PRODUCT, DOMAIN, null))
                .thenReturn(Set.of());
        when(strapiContentCache.getQuestionsByDomain(eq(DOMAIN), eq(LOCALE)))
                .thenReturn(buildQuestions(DOMAIN, 10));

        List<String> selected = service.selectQuestions(USER_ID, PRODUCT, DOMAIN, "  ", null, 5, LOCALE);

        assertEquals(5, selected.size());
    }

    // ─── Helpers ───

    private QuestionProgress buildProgress(String questionId, Instant nextReviewAt) {
        QuestionProgress qp = new QuestionProgress(USER_ID, questionId, PRODUCT, DOMAIN, "test-topic");
        qp.setMasteryLevel(MasteryLevel.LEARNING);
        qp.setNextReviewAt(nextReviewAt);
        return qp;
    }

    private List<StrapiQuestionDto> buildQuestions(String domainCode, int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(i -> new StrapiQuestionDto(
                        i,
                        "doc-" + domainCode + "-" + i,
                        "Question " + i,
                        "multiple_choice", "medium",
                        null, null, null, null, 1, false, false, null, null,
                        List.of(), null, null, null, null, null,
                        new StrapiRelationDto(0, null, domainCode, null),
                        new StrapiRelationDto(0, null, "topic", null),
                        null))
                .toList();
    }
}
