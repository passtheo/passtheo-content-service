package com.passtheo.content.repository;

import com.passtheo.content.domain.entity.SessionAnswer;
import jakarta.annotation.Nonnull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Repository for SessionAnswer entities.
 */
@Repository
public interface SessionAnswerRepository extends JpaRepository<SessionAnswer, UUID> {

    /**
     * Finds all answers for a session.
     *
     * @param sessionId the session ID
     * @return list of answers ordered by question order
     */
    List<SessionAnswer> findBySessionIdOrderByQuestionOrderAsc(@Nonnull UUID sessionId);

    /**
     * Finds an existing answer for a specific question in a session (duplicate check).
     *
     * @param sessionId        the session ID
     * @param strapiQuestionId the Strapi question ID
     * @return the existing answer, if present
     */
    java.util.Optional<SessionAnswer> findBySessionIdAndStrapiQuestionId(
            @Nonnull UUID sessionId, @Nonnull String strapiQuestionId);

    /**
     * Counts answers in a session.
     *
     * @param sessionId the session ID
     * @return the answer count
     */
    long countBySessionId(@Nonnull UUID sessionId);

    /**
     * Finds distinct dates on which the user answered at least one question.
     * Used to compute the lastSevenDays streak dots.
     *
     * @param userId      the user's Keycloak ID
     * @param productCode the product code
     * @param startDate   start of the date range (inclusive)
     * @param endDate     end of the date range (exclusive)
     * @return list of distinct study dates within the range
     */
    @Query(value = """
            SELECT DISTINCT CAST(sa.answered_at AS DATE)
            FROM session_answers sa
            JOIN study_sessions ss ON sa.session_id = ss.id
            WHERE ss.keycloak_user_id = :userId
              AND ss.product_code = :productCode
              AND sa.answered_at >= :startDate
              AND sa.answered_at < :endDate
            ORDER BY 1
            """, nativeQuery = true)
    List<LocalDate> findStudyDatesBetween(
            @Param("userId") UUID userId,
            @Param("productCode") String productCode,
            @Param("startDate") Instant startDate,
            @Param("endDate") Instant endDate);

    /**
     * Computes the average answer time in milliseconds for a user/product.
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     * @return average time in ms, or null if no answers
     */
    @Query(value = """
            SELECT AVG(sa.time_taken_ms)
            FROM session_answers sa
            JOIN study_sessions ss ON sa.session_id = ss.id
            WHERE ss.keycloak_user_id = :userId
              AND ss.product_code = :productCode
              AND sa.deleted_at IS NULL
              AND ss.deleted_at IS NULL
            """, nativeQuery = true)
    Double averageTimeTakenMs(
            @Param("userId") UUID keycloakUserId,
            @Param("productCode") String productCode);
}
