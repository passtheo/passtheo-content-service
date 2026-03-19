package com.passtheo.content.repository;

import com.passtheo.content.domain.entity.DomainProgress;
import com.passtheo.content.domain.enums.DomainStrength;
import jakarta.annotation.Nonnull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for DomainProgress entities.
 */
@Repository
public interface DomainProgressRepository extends JpaRepository<DomainProgress, UUID> {

    /**
     * Finds all domain progress for a user/product.
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     * @return list of domain progress records
     */
    List<DomainProgress> findByKeycloakUserIdAndProductCode(
            @Nonnull UUID keycloakUserId, @Nonnull String productCode);

    /**
     * Finds a specific domain progress record.
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     * @param domainCode     the domain code
     * @return the domain progress if found
     */
    Optional<DomainProgress> findByKeycloakUserIdAndProductCodeAndDomainCode(
            @Nonnull UUID keycloakUserId, @Nonnull String productCode, @Nonnull String domainCode);

    /**
     * Counts mastered domains for a user/product.
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     * @return count of mastered domains
     */
    long countByKeycloakUserIdAndProductCodeAndStrength(
            @Nonnull UUID keycloakUserId, @Nonnull String productCode, @Nonnull DomainStrength strength);

    /**
     * Deletes all progress for a user (GDPR).
     *
     * @param keycloakUserId the user's Keycloak ID
     */
    void deleteByKeycloakUserId(@Nonnull UUID keycloakUserId);
}
