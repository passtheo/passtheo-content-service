package com.passtheo.content.repository;

import com.passtheo.content.domain.entity.QuestionProgress;
import com.passtheo.content.domain.enums.MasteryLevel;
import jakarta.annotation.Nonnull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

/**
 * Repository for QuestionProgress entities — core spaced repetition data.
 */
@Repository
public interface QuestionProgressRepository extends JpaRepository<QuestionProgress, UUID> {

    /**
     * Per-domain mastery aggregate computed directly from question_progress rows.
     * Used instead of the (never-populated) domain_progress table.
     */
    interface DomainMasteryProjection {
        /**
         * Returns the domain code.
         *
         * @return domain code (e.g. "verkeersborden")
         */
        String getDomainCode();

        /**
         * Returns the number of distinct questions answered at least once.
         *
         * @return attempted question count
         */
        long getAttemptedCount();

        /**
         * Returns the sum of totalCorrect across all answered questions in this domain.
         *
         * @return total correct answers
         */
        long getCorrectCount();

        /**
         * Returns the sum of totalAttempts across all answered questions (denominator for accuracy).
         *
         * @return total answer attempts
         */
        long getTotalAttempts();

        /**
         * Returns the number of questions at MASTERED level.
         *
         * @return mastered question count
         */
        long getMasteredCount();

        /**
         * Returns the number of questions at LEARNING or FAMILIAR level.
         *
         * @return learning question count
         */
        long getLearningCount();
    }

    /**
     * Returns domain-level mastery aggregates for a user/product in a single query.
     *
     * @param userId      the user's Keycloak ID
     * @param productCode the product code
     * @return one projection per domain the user has touched
     */
    @Query("SELECT qp.domainCode AS domainCode, " +
           "COUNT(qp) AS attemptedCount, " +
           "SUM(qp.totalCorrect) AS correctCount, " +
           "SUM(qp.totalAttempts) AS totalAttempts, " +
           "SUM(CASE WHEN qp.masteryLevel = 'MASTERED' THEN 1 ELSE 0 END) AS masteredCount, " +
           "SUM(CASE WHEN qp.masteryLevel IN ('LEARNING', 'FAMILIAR') THEN 1 ELSE 0 END) AS learningCount " +
           "FROM QuestionProgress qp WHERE qp.keycloakUserId = :userId " +
           "AND qp.productCode = :productCode AND qp.domainCode <> '' " +
           "GROUP BY qp.domainCode")
    List<DomainMasteryProjection> aggregateByDomain(@Nonnull @Param("userId") UUID userId,
                                                     @Nonnull @Param("productCode") String productCode);

    /**
     * Per-topic mastery aggregate computed directly from question_progress rows.
     * Used instead of the (never-populated) topic_progress table.
     */
    interface TopicMasteryProjection {
        /**
         * Returns the topic code.
         *
         * @return topic code (e.g. "verbodsborden")
         */
        String getTopicCode();

        /**
         * Returns the number of distinct questions answered at least once.
         *
         * @return attempted question count
         */
        long getAttemptedCount();

        /**
         * Returns the sum of totalCorrect across all answered questions in this topic.
         *
         * @return total correct answers
         */
        long getCorrectCount();

        /**
         * Returns the sum of totalAttempts across all answered questions (denominator for accuracy).
         *
         * @return total answer attempts
         */
        long getTotalAttempts();

        /**
         * Returns the number of questions at MASTERED level.
         *
         * @return mastered question count
         */
        long getMasteredCount();
    }

    /**
     * Returns topic-level mastery aggregates for a user/product/domain in a single query.
     *
     * @param userId      the user's Keycloak ID
     * @param productCode the product code
     * @param domainCode  the domain code
     * @return one projection per topic the user has touched within the domain
     */
    @Query("SELECT qp.topicCode AS topicCode, " +
           "COUNT(qp) AS attemptedCount, " +
           "SUM(qp.totalCorrect) AS correctCount, " +
           "SUM(qp.totalAttempts) AS totalAttempts, " +
           "SUM(CASE WHEN qp.masteryLevel = 'MASTERED' THEN 1 ELSE 0 END) AS masteredCount " +
           "FROM QuestionProgress qp WHERE qp.keycloakUserId = :userId " +
           "AND qp.productCode = :productCode AND qp.domainCode = :domainCode " +
           "AND qp.topicCode <> '' " +
           "GROUP BY qp.topicCode")
    List<TopicMasteryProjection> aggregateByTopic(@Nonnull @Param("userId") UUID userId,
                                                   @Nonnull @Param("productCode") String productCode,
                                                   @Nonnull @Param("domainCode") String domainCode);

    /**
     * Finds a progress record by user and question.
     *
     * @param keycloakUserId   the user's Keycloak ID
     * @param strapiQuestionId the Strapi question ID
     * @return the progress record if found
     */
    Optional<QuestionProgress> findByKeycloakUserIdAndStrapiQuestionId(
            @Nonnull UUID keycloakUserId, @Nonnull String strapiQuestionId);

    /**
     * Finds questions due for review (nextReviewAt before now).
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     * @param domainCode     the domain code (nullable for all domains)
     * @param now            the current time
     * @return list of due questions sorted by most overdue
     */
    @Query("SELECT qp FROM QuestionProgress qp WHERE qp.keycloakUserId = :userId " +
           "AND qp.productCode = :productCode " +
           "AND (:domainCode IS NULL OR qp.domainCode = :domainCode) " +
           "AND qp.nextReviewAt IS NOT NULL AND qp.nextReviewAt < :now " +
           "ORDER BY qp.nextReviewAt ASC")
    List<QuestionProgress> findDueReviews(
            @Param("userId") UUID keycloakUserId,
            @Param("productCode") String productCode,
            @Param("domainCode") String domainCode,
            @Param("now") Instant now,
            Pageable pageable);

    /**
     * Finds weak questions (LEARNING with low consecutive correct).
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     * @param domainCode     the domain code (nullable)
     * @param maxConsecutive maximum consecutive correct threshold
     * @return list of weak questions
     */
    @Query("SELECT qp FROM QuestionProgress qp WHERE qp.keycloakUserId = :userId " +
           "AND qp.productCode = :productCode " +
           "AND (:domainCode IS NULL OR qp.domainCode = :domainCode) " +
           "AND qp.masteryLevel = 'LEARNING' AND qp.consecutiveCorrect < :maxConsecutive " +
           "ORDER BY qp.consecutiveCorrect ASC, qp.lastAnsweredAt ASC")
    List<QuestionProgress> findWeak(
            @Param("userId") UUID keycloakUserId,
            @Param("productCode") String productCode,
            @Param("domainCode") String domainCode,
            @Param("maxConsecutive") int maxConsecutive,
            Pageable pageable);

    /**
     * Finds IDs of questions the user has already seen.
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     * @param domainCode     the domain code (nullable)
     * @return set of seen question IDs
     */
    @Query("SELECT qp.strapiQuestionId FROM QuestionProgress qp WHERE qp.keycloakUserId = :userId " +
           "AND qp.productCode = :productCode " +
           "AND (:domainCode IS NULL OR qp.domainCode = :domainCode)")
    Set<String> findSeenQuestionIds(
            @Param("userId") UUID keycloakUserId,
            @Param("productCode") String productCode,
            @Param("domainCode") String domainCode);

    /**
     * Finds FAMILIAR questions sorted by nearest review date.
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     * @param domainCode     the domain code (nullable)
     * @return list of familiar questions
     */
    @Query("SELECT qp FROM QuestionProgress qp WHERE qp.keycloakUserId = :userId " +
           "AND qp.productCode = :productCode " +
           "AND (:domainCode IS NULL OR qp.domainCode = :domainCode) " +
           "AND qp.masteryLevel = 'FAMILIAR' " +
           "ORDER BY qp.nextReviewAt ASC NULLS LAST")
    List<QuestionProgress> findFamiliarSorted(
            @Param("userId") UUID keycloakUserId,
            @Param("productCode") String productCode,
            @Param("domainCode") String domainCode,
            Pageable pageable);

    /**
     * Counts total questions attempted by a user for a product.
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     * @return count of attempted questions
     */
    @Query("SELECT COUNT(qp) FROM QuestionProgress qp WHERE qp.keycloakUserId = :userId " +
           "AND qp.productCode = :productCode AND qp.totalAttempts > 0")
    int countAttempted(@Param("userId") UUID keycloakUserId, @Param("productCode") String productCode);

    /**
     * Counts total correct answers by a user for a product.
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     * @return sum of correct answers
     */
    @Query("SELECT COALESCE(SUM(qp.totalCorrect), 0) FROM QuestionProgress qp WHERE qp.keycloakUserId = :userId " +
           "AND qp.productCode = :productCode")
    int countCorrect(@Param("userId") UUID keycloakUserId, @Param("productCode") String productCode);

    /**
     * Counts questions at each mastery level.
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     * @param masteryLevel   the mastery level
     * @return count at that level
     */
    long countByKeycloakUserIdAndProductCodeAndMasteryLevel(
            @Nonnull UUID keycloakUserId, @Nonnull String productCode, @Nonnull MasteryLevel masteryLevel);

    /**
     * Finds all progress records for a user/product/domain.
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     * @param domainCode     the domain code
     * @return list of progress records
     */
    List<QuestionProgress> findByKeycloakUserIdAndProductCodeAndDomainCode(
            @Nonnull UUID keycloakUserId, @Nonnull String productCode, @Nonnull String domainCode);

    /**
     * Finds the maximum consecutive correct streak for a user/product.
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     * @return max consecutive correct
     */
    @Query("SELECT COALESCE(MAX(qp.consecutiveCorrect), 0) FROM QuestionProgress qp " +
           "WHERE qp.keycloakUserId = :userId AND qp.productCode = :productCode")
    int maxConsecutiveCorrect(@Param("userId") UUID keycloakUserId, @Param("productCode") String productCode);

    /**
     * Deletes all progress for a user (GDPR).
     *
     * @param keycloakUserId the user's Keycloak ID
     */
    void deleteByKeycloakUserId(@Nonnull UUID keycloakUserId);
}
