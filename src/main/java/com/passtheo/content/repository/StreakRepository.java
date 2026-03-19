package com.passtheo.content.repository;

import com.passtheo.content.domain.entity.Streak;
import jakarta.annotation.Nonnull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Streak entities.
 */
@Repository
public interface StreakRepository extends JpaRepository<Streak, UUID> {

    /**
     * Finds the streak for a user/product.
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     * @return the streak if found
     */
    Optional<Streak> findByKeycloakUserIdAndProductCode(
            @Nonnull UUID keycloakUserId, @Nonnull String productCode);

    /**
     * Deletes all streaks for a user (GDPR).
     *
     * @param keycloakUserId the user's Keycloak ID
     */
    void deleteByKeycloakUserId(@Nonnull UUID keycloakUserId);
}
