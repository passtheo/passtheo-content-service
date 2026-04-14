package com.passtheo.content.repository;

import com.passtheo.content.domain.entity.ExamAttempt;
import jakarta.annotation.Nonnull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ExamAttempt entities.
 */
@Repository
public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, UUID> {

    /**
     * Finds exam history for a user/product.
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     * @param pageable       pagination info
     * @return page of exam attempts
     */
    Page<ExamAttempt> findByKeycloakUserIdAndProductCodeOrderByCompletedAtDesc(
            @Nonnull UUID keycloakUserId, @Nonnull String productCode, @Nonnull Pageable pageable);

    /**
     * Finds the best exam score for a user/product.
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     * @return the best correct count, or null if no exams taken
     */
    @Query("SELECT MAX(ea.correctCount) FROM ExamAttempt ea WHERE ea.keycloakUserId = :userId " +
           "AND ea.productCode = :productCode AND ea.deletedAt IS NULL")
    Integer findBestScore(@Param("userId") UUID keycloakUserId, @Param("productCode") String productCode);

    /**
     * Counts passed exams for a user/product.
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     * @return count of passed exams
     */
    long countByKeycloakUserIdAndProductCodeAndPassedTrue(
            @Nonnull UUID keycloakUserId, @Nonnull String productCode);

    /**
     * Counts perfect exams (100% score) for a user/product.
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     * @return count of perfect exams
     */
    @Query("SELECT COUNT(ea) FROM ExamAttempt ea WHERE ea.keycloakUserId = :userId " +
           "AND ea.productCode = :productCode AND ea.correctCount = ea.totalQuestions AND ea.deletedAt IS NULL")
    long countPerfect(@Param("userId") UUID keycloakUserId, @Param("productCode") String productCode);

    /**
     * Counts exams taken by a user after a given cutoff (for free tier weekly limit).
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     * @param cutoff         earliest timestamp to count (e.g. 7 days ago)
     * @return count of recent exams
     */
    @Query("SELECT COUNT(ea) FROM ExamAttempt ea WHERE ea.keycloakUserId = :userId " +
           "AND ea.productCode = :productCode AND ea.completedAt > :cutoff AND ea.deletedAt IS NULL")
    long countRecentExams(@Param("userId") UUID keycloakUserId,
                          @Param("productCode") String productCode,
                          @Param("cutoff") Instant cutoff);

    /**
     * Counts total completed exams for a user/product (regardless of pass/fail).
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     * @return count of completed exams
     */
    @Query("SELECT COUNT(ea) FROM ExamAttempt ea WHERE ea.keycloakUserId = :userId " +
           "AND ea.productCode = :productCode AND ea.deletedAt IS NULL")
    long countCompletedExams(@Param("userId") UUID keycloakUserId, @Param("productCode") String productCode);

    /**
     * Finds an exam attempt by ID and user.
     *
     * @param id             the exam attempt ID
     * @param keycloakUserId the user's Keycloak ID
     * @return the exam attempt if found
     */
    Optional<ExamAttempt> findByIdAndKeycloakUserId(@Nonnull UUID id, @Nonnull UUID keycloakUserId);

    /**
     * Computes the average correctCount across all exam attempts for a user/product.
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     * @return average correct count, or null if no exams taken
     */
    @Query("SELECT AVG(ea.correctCount) FROM ExamAttempt ea WHERE ea.keycloakUserId = :userId " +
           "AND ea.productCode = :productCode AND ea.deletedAt IS NULL")
    Double findAverageScore(@Param("userId") UUID keycloakUserId, @Param("productCode") String productCode);

    /**
     * Deletes all exams for a user (GDPR).
     *
     * @param keycloakUserId the user's Keycloak ID
     */
    void deleteByKeycloakUserId(@Nonnull UUID keycloakUserId);
}
