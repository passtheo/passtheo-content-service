package com.passtheo.content.repository;

import com.passtheo.content.domain.entity.UserXp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for user XP records.
 */
public interface UserXpRepository extends JpaRepository<UserXp, UUID> {

    /**
     * Finds the XP record for a user and product.
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     * @return the user XP record if it exists
     */
    Optional<UserXp> findByKeycloakUserIdAndProductCode(UUID keycloakUserId, String productCode);
}
