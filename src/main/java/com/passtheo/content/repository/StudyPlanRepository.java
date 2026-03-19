package com.passtheo.content.repository;

import com.passtheo.content.domain.entity.StudyPlan;
import com.passtheo.content.domain.enums.PlanStatus;
import jakarta.annotation.Nonnull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for StudyPlan entities.
 */
@Repository
public interface StudyPlanRepository extends JpaRepository<StudyPlan, UUID> {

    /**
     * Finds the active study plan for a user/product.
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     * @param status         the plan status
     * @return the active plan if found
     */
    Optional<StudyPlan> findByKeycloakUserIdAndProductCodeAndStatus(
            @Nonnull UUID keycloakUserId, @Nonnull String productCode, @Nonnull PlanStatus status);

    /**
     * Deletes all plans for a user (GDPR).
     *
     * @param keycloakUserId the user's Keycloak ID
     */
    void deleteByKeycloakUserId(@Nonnull UUID keycloakUserId);
}
