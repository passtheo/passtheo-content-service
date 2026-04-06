package com.passtheo.content.service;

import com.passtheo.content.domain.entity.QuestionProgress;
import com.passtheo.content.integration.strapi.StrapiContentCache;
import com.passtheo.content.repository.QuestionProgressRepository;
import com.passtheo.content.domain.enums.SessionType;
import com.passtheo.shared.core.exception.AppException;
import com.passtheo.shared.core.exception.ErrorCode;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;

/**
 * Selects optimal questions for practice sessions using a modified SM-2 algorithm.
 * Priority order: (1) due reviews, (2) weak questions, (3) new/unseen, (4) familiar reinforcement.
 * Max interval capped at 14 days for exam prep (not lifetime learning).
 */
@Service
public class QuestionSelectionService {

    private static final Logger LOG = LoggerFactory.getLogger(QuestionSelectionService.class);
    private static final int WEAK_CONSECUTIVE_THRESHOLD = 2;

    private final QuestionProgressRepository progressRepository;
    private final StrapiContentCache strapiContentCache;

    /**
     * Constructs the question selection service.
     *
     * @param progressRepository the question progress repository
     * @param strapiContentCache the Strapi content cache
     */
    public QuestionSelectionService(QuestionProgressRepository progressRepository,
                                    StrapiContentCache strapiContentCache) {
        this.progressRepository = progressRepository;
        this.strapiContentCache = strapiContentCache;
    }

    /** Minimum number of questions required for a WEAK_REVIEW session. */
    private static final int MIN_WEAK_REVIEW_COUNT = 5;

    /**
     * Selects N questions for a practice session using spaced repetition priorities.
     * Coverage guarantee: students must see ALL questions at least once.
     *
     * <p>For {@link SessionType#WEAK_REVIEW} sessions, only due reviews (P1) and weak/LEARNING
     * questions (P2) are selected — new and familiar questions are skipped. If fewer than
     * {@value #MIN_WEAK_REVIEW_COUNT} weak questions are available, a 400 error is thrown.</p>
     *
     * @param userId      the user's Keycloak ID
     * @param productCode the product code
     * @param domainCode  the domain code (nullable for mixed/all domains)
     * @param topicCode   the topic code (nullable — when non-null, narrows to topic)
     * @param sessionType the session type (nullable defaults to PRACTICE behaviour)
     * @param count       number of questions to select
     * @param locale      content locale for Strapi lookups
     * @return ordered list of Strapi question IDs
     */
    public List<String> selectQuestions(@Nonnull UUID userId, @Nonnull String productCode,
                                        String domainCode, @Nullable String topicCode,
                                        @Nullable SessionType sessionType,
                                        int count, @Nonnull String locale) {
        List<String> selected = new ArrayList<>();
        int dueAdded = 0;
        int weakAdded = 0;
        int newAdded = 0;
        int fillAdded = 0;

        // Normalise blank topicCode to null for consistent IS NULL handling in queries.
        String effectiveTopic = (topicCode != null && !topicCode.isBlank()) ? topicCode : null;

        // Priority 1: Due reviews (nextReviewAt < now) — most overdue first
        List<QuestionProgress> dueReviews = progressRepository
                .findDueReviews(userId, productCode, domainCode, effectiveTopic, Instant.now(), Pageable.ofSize(count));
        dueReviews.sort(Comparator.comparing(QuestionProgress::getNextReviewAt));
        LOG.debug("Question selection P1 due-reviews: user={}, available={}", userId, dueReviews.size());
        for (QuestionProgress qp : dueReviews) {
            if (selected.size() >= count) {
                break;
            }
            selected.add(qp.getStrapiQuestionId());
            dueAdded++;
        }

        // Priority 2: Weak questions (LEARNING with consecutiveCorrect < threshold)
        if (selected.size() < count) {
            List<QuestionProgress> weak = progressRepository
                    .findWeak(userId, productCode, domainCode, effectiveTopic, WEAK_CONSECUTIVE_THRESHOLD, Pageable.ofSize(count));
            LOG.debug("Question selection P2 weak: user={}, available={}", userId, weak.size());
            for (QuestionProgress qp : weak) {
                if (selected.size() >= count) {
                    break;
                }
                if (!selected.contains(qp.getStrapiQuestionId())) {
                    selected.add(qp.getStrapiQuestionId());
                    weakAdded++;
                }
            }
        }

        // WEAK_REVIEW: only due reviews + weak questions — no new or familiar
        if (sessionType == SessionType.WEAK_REVIEW) {
            if (selected.size() < MIN_WEAK_REVIEW_COUNT) {
                throw new AppException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                        "No weak questions to review — keep practicing!");
            }
            LOG.debug("WEAK_REVIEW selection COMPLETE: user={}, product={}, domain={}, topic={}, total={} [due={}, weak={}]",
                    userId, productCode, domainCode, effectiveTopic, selected.size(), dueAdded, weakAdded);
            return List.copyOf(selected);
        }

        // Priority 3: New (unseen) questions — shuffled randomly for variety
        if (selected.size() < count) {
            List<String> allQuestionIds = getAllQuestionIds(productCode, domainCode, effectiveTopic, locale);
            Set<String> seenIds = progressRepository
                    .findSeenQuestionIds(userId, productCode, domainCode, effectiveTopic);

            List<String> newIds = allQuestionIds.stream()
                    .filter(id -> !seenIds.contains(id) && !selected.contains(id))
                    .collect(Collectors.toCollection(ArrayList::new));
            Collections.shuffle(newIds);
            LOG.debug("Question selection P3 new: user={}, total={}, seen={}, available={}",
                    userId, allQuestionIds.size(), seenIds.size(), newIds.size());

            for (String id : newIds) {
                if (selected.size() >= count) {
                    break;
                }
                selected.add(id);
                newAdded++;
            }
        }

        // Priority 4: Fill with FAMILIAR questions closest to review date
        if (selected.size() < count) {
            List<QuestionProgress> familiar = progressRepository
                    .findFamiliarSorted(userId, productCode, domainCode, effectiveTopic, Pageable.ofSize(count));
            LOG.debug("Question selection P4 familiar-fill: user={}, available={}", userId, familiar.size());
            for (QuestionProgress qp : familiar) {
                if (selected.size() >= count) {
                    break;
                }
                if (!selected.contains(qp.getStrapiQuestionId())) {
                    selected.add(qp.getStrapiQuestionId());
                    fillAdded++;
                }
            }
        }

        LOG.debug("Question selection COMPLETE: user={}, product={}, domain={}, topic={}, total={} [due={}, weak={}, new={}, fill={}]",
                userId, productCode, domainCode, effectiveTopic, selected.size(),
                dueAdded, weakAdded, newAdded, fillAdded);

        if (selected.isEmpty()) {
            LOG.warn("No questions selected: user={}, product={}, domain={}, topic={}, requestedCount={}",
                    userId, productCode, domainCode, effectiveTopic, count);
        }

        return List.copyOf(selected);
    }

    /**
     * Gets all question IDs for a product/domain from Strapi cache.
     *
     * @param productCode the product code
     * @param domainCode  the domain code (nullable for all domains)
     * @param locale      the content locale
     * @return list of question IDs
     */
    private List<String> getAllQuestionIds(@Nonnull String productCode, String domainCode,
                                           @Nullable String topicCode, @Nonnull String locale) {
        if (topicCode != null) {
            return strapiContentCache.getQuestionsByTopic(topicCode, locale).stream()
                    .map(q -> q.documentId())
                    .toList();
        }
        if (domainCode != null && !domainCode.isBlank()) {
            return strapiContentCache.getQuestionsByDomain(domainCode, locale).stream()
                    .map(q -> q.documentId())
                    .toList();
        }
        return strapiContentCache.getQuestionIds(productCode, locale);
    }
}
