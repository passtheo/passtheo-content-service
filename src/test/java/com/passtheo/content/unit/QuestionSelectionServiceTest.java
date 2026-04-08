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
        // Active pool includes the due-review ID + 9 others
        List<StrapiQuestionDto> pool = new ArrayList<>(buildQuestions(DOMAIN, 9));
        pool.add(buildSingleQuestion("q-due-1"));
        when(strapiContentCache.getQuestionsByDomain(eq(DOMAIN), eq(LOCALE)))
                .thenReturn(pool);

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
        // Active pool includes the weak question ID
        List<StrapiQuestionDto> pool = new ArrayList<>(buildQuestions(DOMAIN, 9));
        pool.add(buildSingleQuestion("q-weak-1"));
        when(strapiContentCache.getQuestionsByDomain(eq(DOMAIN), eq(LOCALE)))
                .thenReturn(pool);

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
        // Active pool includes the progress question ID
        List<StrapiQuestionDto> pool = new ArrayList<>(buildQuestions(DOMAIN, 4));
        pool.add(buildSingleQuestion("q-1"));
        when(strapiContentCache.getQuestionsByDomain(eq(DOMAIN), eq(LOCALE)))
                .thenReturn(pool);

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
                .thenReturn(Set.of("q-familiar-1"));
        // Active pool includes only the familiar question — no new unseen questions
        when(strapiContentCache.getQuestionsByDomain(eq(DOMAIN), eq(LOCALE)))
                .thenReturn(buildQuestionsWithIds(List.of("q-familiar-1")));
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
        // Active pool must contain the due/weak question IDs
        when(strapiContentCache.getQuestionsByDomain(eq(DOMAIN), eq(LOCALE)))
                .thenReturn(buildQuestionsWithIds(List.of("q-due", "q-weak-1", "q-weak-2", "q-weak-3", "q-weak-4", "q-new-1")));

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
        when(strapiContentCache.getQuestionsByDomain(eq(DOMAIN), eq(LOCALE)))
                .thenReturn(buildQuestionsWithIds(List.of("q-due")));

        org.junit.jupiter.api.Assertions.assertThrows(AppException.class, () ->
                service.selectQuestions(USER_ID, PRODUCT, DOMAIN, null,
                        SessionType.WEAK_REVIEW, 10, LOCALE));
    }

    // ─── Deactivated question filtering ───

    @Test
    void dueReviews_skipsDeactivatedQuestions() {
        // q-due-1 exists in active pool, q-due-2 was deactivated
        QuestionProgress due1 = buildProgress("q-due-1", Instant.now().minus(1, ChronoUnit.DAYS));
        QuestionProgress due2 = buildProgress("q-due-2", Instant.now().minus(2, ChronoUnit.DAYS));
        when(progressRepository.findDueReviews(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), isNull(), any(Instant.class), any(Pageable.class)))
                .thenReturn(new ArrayList<>(List.of(due1, due2)));
        when(progressRepository.findWeak(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), isNull(), anyInt(), any(Pageable.class)))
                .thenReturn(List.of());
        when(progressRepository.findSeenQuestionIds(USER_ID, PRODUCT, DOMAIN, null))
                .thenReturn(Set.of("q-due-1", "q-due-2"));
        // Active pool only has q-due-1 + new questions (q-due-2 was deactivated)
        when(strapiContentCache.getQuestionsByDomain(eq(DOMAIN), eq(LOCALE)))
                .thenReturn(buildQuestionsWithIds(List.of("q-due-1", "q-new-1", "q-new-2", "q-new-3", "q-new-4")));

        List<String> selected = service.selectQuestions(USER_ID, PRODUCT, DOMAIN, null, null, 5, LOCALE);

        assertTrue(selected.contains("q-due-1"), "Active due review should be selected");
        assertFalse(selected.contains("q-due-2"), "Deactivated question should be excluded");
        assertEquals(5, selected.size());
    }

    @Test
    void weakQuestions_skipsDeactivatedQuestions() {
        QuestionProgress weak1 = buildProgress("q-weak-active", null);
        QuestionProgress weak2 = buildProgress("q-weak-deactivated", null);
        when(progressRepository.findDueReviews(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), isNull(), any(Instant.class), any(Pageable.class)))
                .thenReturn(new ArrayList<>());
        when(progressRepository.findWeak(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), isNull(), anyInt(), any(Pageable.class)))
                .thenReturn(List.of(weak1, weak2));
        when(progressRepository.findSeenQuestionIds(USER_ID, PRODUCT, DOMAIN, null))
                .thenReturn(Set.of("q-weak-active", "q-weak-deactivated"));
        // Only q-weak-active is in the active pool
        when(strapiContentCache.getQuestionsByDomain(eq(DOMAIN), eq(LOCALE)))
                .thenReturn(buildQuestionsWithIds(List.of("q-weak-active", "q-new-1", "q-new-2", "q-new-3", "q-new-4")));

        List<String> selected = service.selectQuestions(USER_ID, PRODUCT, DOMAIN, null, null, 5, LOCALE);

        assertTrue(selected.contains("q-weak-active"), "Active weak question should be selected");
        assertFalse(selected.contains("q-weak-deactivated"), "Deactivated weak question should be excluded");
    }

    @Test
    void familiarFill_skipsDeactivatedQuestions() {
        // Request 5 questions but only 3 active exist — P4 (familiar) will be tried to fill the gap.
        // The familiar question is deactivated so it should be skipped.
        QuestionProgress familiar = buildProgress("q-fam-deactivated", Instant.now().plus(3, ChronoUnit.DAYS));
        when(progressRepository.findDueReviews(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), isNull(), any(Instant.class), any(Pageable.class)))
                .thenReturn(new ArrayList<>());
        when(progressRepository.findWeak(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), isNull(), anyInt(), any(Pageable.class)))
                .thenReturn(List.of());
        // Active pool only has 3 new questions — q-fam-deactivated is NOT in the pool
        when(strapiContentCache.getQuestionsByDomain(eq(DOMAIN), eq(LOCALE)))
                .thenReturn(buildQuestions(DOMAIN, 3));
        when(progressRepository.findSeenQuestionIds(USER_ID, PRODUCT, DOMAIN, null))
                .thenReturn(Set.of("q-fam-deactivated"));
        when(progressRepository.findFamiliarSorted(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), isNull(), any(Pageable.class)))
                .thenReturn(List.of(familiar));

        List<String> selected = service.selectQuestions(USER_ID, PRODUCT, DOMAIN, null, null, 5, LOCALE);

        assertFalse(selected.contains("q-fam-deactivated"), "Deactivated familiar question should be excluded");
        assertEquals(3, selected.size(), "Only 3 active questions available");
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

    // ─── Catch-all (P5) ───

    @Test
    void catchAll_includesMasteredQuestionsNotCoveredByP1toP4() {
        // Scenario: topic has 12 questions, user has seen all of them.
        // P1 returns 2 due reviews, P2 returns 1 weak, P3 returns 0 new (all seen),
        // P4 returns 2 familiar. Without P5, only 5 selected out of 12.
        // With P5, the remaining 7 (MASTERED, not-yet-due) are added.
        QuestionProgress due1 = buildProgress("doc-domain-1", Instant.now().minus(1, ChronoUnit.DAYS));
        QuestionProgress due2 = buildProgress("doc-domain-2", Instant.now().minus(2, ChronoUnit.DAYS));
        QuestionProgress weak = buildProgress("doc-domain-3", null);
        weak.setConsecutiveCorrect(1);
        QuestionProgress fam1 = buildProgress("doc-domain-4", Instant.now().plus(3, ChronoUnit.DAYS));
        QuestionProgress fam2 = buildProgress("doc-domain-5", Instant.now().plus(5, ChronoUnit.DAYS));

        when(progressRepository.findDueReviews(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), eq(TOPIC), any(Instant.class), any(Pageable.class)))
                .thenReturn(new ArrayList<>(List.of(due1, due2)));
        when(progressRepository.findWeak(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), eq(TOPIC), anyInt(), any(Pageable.class)))
                .thenReturn(List.of(weak));
        // All 12 questions have been seen
        when(progressRepository.findSeenQuestionIds(USER_ID, PRODUCT, DOMAIN, TOPIC))
                .thenReturn(Set.of("doc-domain-1", "doc-domain-2", "doc-domain-3", "doc-domain-4",
                        "doc-domain-5", "doc-domain-6", "doc-domain-7", "doc-domain-8",
                        "doc-domain-9", "doc-domain-10", "doc-domain-11", "doc-domain-12"));
        when(strapiContentCache.getQuestionsByTopic(eq(TOPIC), eq(LOCALE)))
                .thenReturn(buildQuestions("domain", 12));
        when(progressRepository.findFamiliarSorted(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), eq(TOPIC), any(Pageable.class)))
                .thenReturn(List.of(fam1, fam2));

        List<String> selected = service.selectQuestions(USER_ID, PRODUCT, DOMAIN, TOPIC, SessionType.PRACTICE, 12, LOCALE);

        assertEquals(12, selected.size(), "All 12 topic questions should be selected");
        long distinctCount = selected.stream().distinct().count();
        assertEquals(12, distinctCount, "No duplicates");
    }

    @Test
    void catchAll_doesNotExceedRequestedCount() {
        // Domain-level practice requesting 5 out of 10 — P5 should NOT overshoot count.
        when(progressRepository.findDueReviews(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), isNull(), any(Instant.class), any(Pageable.class)))
                .thenReturn(new ArrayList<>());
        when(progressRepository.findWeak(eq(USER_ID), eq(PRODUCT), eq(DOMAIN), isNull(), anyInt(), any(Pageable.class)))
                .thenReturn(List.of());
        when(progressRepository.findSeenQuestionIds(USER_ID, PRODUCT, DOMAIN, null))
                .thenReturn(Set.of());
        when(strapiContentCache.getQuestionsByDomain(eq(DOMAIN), eq(LOCALE)))
                .thenReturn(buildQuestions(DOMAIN, 10));

        List<String> selected = service.selectQuestions(USER_ID, PRODUCT, DOMAIN, null, SessionType.PRACTICE, 5, LOCALE);

        assertEquals(5, selected.size(), "Should not exceed requested count");
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

    private StrapiQuestionDto buildSingleQuestion(String documentId) {
        return new StrapiQuestionDto(
                0, documentId, "Question " + documentId,
                "multiple_choice", "medium",
                null, null, null, null, 1, false, false, null, null,
                List.of(), null, null, null, null, null,
                new StrapiRelationDto(0, null, DOMAIN, null),
                new StrapiRelationDto(0, null, "topic", null),
                null);
    }

    private List<StrapiQuestionDto> buildQuestionsWithIds(List<String> ids) {
        return java.util.stream.IntStream.range(0, ids.size())
                .mapToObj(i -> new StrapiQuestionDto(
                        i + 1,
                        ids.get(i),
                        "Question " + ids.get(i),
                        "multiple_choice", "medium",
                        null, null, null, null, 1, false, false, null, null,
                        List.of(), null, null, null, null, null,
                        new StrapiRelationDto(0, null, DOMAIN, null),
                        new StrapiRelationDto(0, null, "topic", null),
                        null))
                .toList();
    }
}
