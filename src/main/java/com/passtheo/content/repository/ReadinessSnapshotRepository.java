package com.passtheo.content.repository;

import com.passtheo.content.domain.entity.ReadinessSnapshot;
import jakarta.annotation.Nonnull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Repository for ReadinessSnapshot entities.
 */
@Repository
public interface ReadinessSnapshotRepository extends JpaRepository<ReadinessSnapshot, UUID> {

    /**
     * Finds readiness trend snapshots for a user/product within a date range.
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     * @param since          the start date
     * @return list of snapshots ordered by date
     */
    List<ReadinessSnapshot> findByKeycloakUserIdAndProductCodeAndSnapshotDateAfterOrderBySnapshotDateAsc(
            @Nonnull UUID keycloakUserId, @Nonnull String productCode, @Nonnull LocalDate since);

    /**
     * Checks whether a snapshot already exists for a user/product/date.
     * Used by ReadinessSnapshotJob to prevent duplicate snapshots.
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     * @param snapshotDate   the date
     * @return true if a snapshot already exists
     */
    boolean existsByKeycloakUserIdAndProductCodeAndSnapshotDate(
            @Nonnull UUID keycloakUserId, @Nonnull String productCode, @Nonnull LocalDate snapshotDate);

    /**
     * Deletes all snapshots for a user (GDPR).
     *
     * @param keycloakUserId the user's Keycloak ID
     */
    void deleteByKeycloakUserId(@Nonnull UUID keycloakUserId);
}
