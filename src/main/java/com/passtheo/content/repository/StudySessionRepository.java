package com.passtheo.content.repository;

import com.passtheo.content.domain.entity.StudySession;
import com.passtheo.content.domain.enums.SessionStatus;
import jakarta.annotation.Nonnull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for StudySession entities.
 */
@Repository
public interface StudySessionRepository extends JpaRepository<StudySession, UUID> {

    /**
     * Finds a session by ID and user.
     *
     * @param id             the session ID
     * @param keycloakUserId the user's Keycloak ID
     * @return the session if found
     */
    Optional<StudySession> findByIdAndKeycloakUserId(@Nonnull UUID id, @Nonnull UUID keycloakUserId);

    /**
     * Finds abandoned sessions (IN_PROGRESS with no activity for given threshold).
     *
     * @param status    the session status
     * @param threshold the inactivity threshold
     * @return list of abandoned sessions
     */
    List<StudySession> findByStatusAndLastActivityAtBefore(
            @Nonnull SessionStatus status, @Nonnull Instant threshold);

    /**
     * Finds the most recently active IN_PROGRESS session for a user and product.
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     * @param status         the session status (IN_PROGRESS)
     * @return the most recent active session, if any
     */
    Optional<StudySession> findFirstByKeycloakUserIdAndProductCodeAndStatusOrderByLastActivityAtDesc(
            @Nonnull UUID keycloakUserId, @Nonnull String productCode, @Nonnull SessionStatus status);

    /**
     * Counts active sessions for a user/product.
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     * @param status         the session status
     * @return count of sessions
     */
    long countByKeycloakUserIdAndProductCodeAndStatus(
            @Nonnull UUID keycloakUserId, @Nonnull String productCode, @Nonnull SessionStatus status);

    /**
     * Counts completed sessions where the user answered every question correctly.
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     * @return count of perfect sessions
     */
    @Query("SELECT COUNT(ss) FROM StudySession ss WHERE ss.keycloakUserId = :userId " +
           "AND ss.productCode = :productCode " +
           "AND ss.status = com.passtheo.content.domain.enums.SessionStatus.COMPLETED " +
           "AND ss.correctCount = ss.totalQuestions AND ss.totalQuestions > 0 " +
           "AND ss.deletedAt IS NULL")
    long countPerfectSessions(@Param("userId") UUID keycloakUserId, @Param("productCode") String productCode);
}
