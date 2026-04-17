package com.passtheo.content.repository;

import com.passtheo.content.domain.entity.LessonProgress;
import jakarta.annotation.Nonnull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for LessonProgress entities.
 */
@Repository
public interface LessonProgressRepository extends JpaRepository<LessonProgress, UUID> {

    /**
     * Finds the progress row for a specific user+product+lesson.
     *
     * @param userId      the user's Keycloak ID
     * @param productCode the product code
     * @param lessonSlug  the lesson slug
     * @return the progress row if present
     */
    Optional<LessonProgress> findByKeycloakUserIdAndProductCodeAndLessonSlug(
            @Nonnull UUID userId, @Nonnull String productCode, @Nonnull String lessonSlug);

    /**
     * Lists all progress rows for a topic.
     *
     * @param userId      the user's Keycloak ID
     * @param productCode the product code
     * @param topicCode   the topic code
     * @return list of progress rows
     */
    List<LessonProgress> findByKeycloakUserIdAndProductCodeAndTopicCode(
            @Nonnull UUID userId, @Nonnull String productCode, @Nonnull String topicCode);

    /**
     * Counts completed lessons for a user/product.
     *
     * @param userId      the user's Keycloak ID
     * @param productCode the product code
     * @return count of completed lessons
     */
    long countByKeycloakUserIdAndProductCodeAndCompletedTrue(
            @Nonnull UUID userId, @Nonnull String productCode);

    /**
     * Counts completed lessons per topic for a user/product.
     *
     * @param userId      the user's Keycloak ID
     * @param productCode the product code
     * @return list of [topicCode, count] pairs
     */
    @Query("SELECT lp.topicCode, COUNT(lp) FROM LessonProgress lp "
            + "WHERE lp.keycloakUserId = :userId AND lp.productCode = :productCode "
            + "AND lp.completed = true GROUP BY lp.topicCode")
    List<Object[]> countCompletedByTopic(@Param("userId") @Nonnull UUID userId,
                                         @Param("productCode") @Nonnull String productCode);
}
