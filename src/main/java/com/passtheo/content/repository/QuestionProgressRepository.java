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

/**
 * Repository for QuestionProgress entities — core spaced repetition data.
 */
@Repository
public interface QuestionProgressRepository extends JpaRepository<QuestionProgress, UUID> {

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
            @Param("now") Instant now);

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
            @Param("maxConsecutive") int maxConsecutive);

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
            @Param("domainCode") String domainCode);

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
