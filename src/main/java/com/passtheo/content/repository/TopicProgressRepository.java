package com.passtheo.content.repository;

import com.passtheo.content.domain.entity.TopicProgress;
import jakarta.annotation.Nonnull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for TopicProgress entities.
 */
@Repository
public interface TopicProgressRepository extends JpaRepository<TopicProgress, UUID> {

    /**
     * Finds topic progress for a user/product/domain.
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     * @param domainCode     the domain code
     * @return list of topic progress records
     */
    List<TopicProgress> findByKeycloakUserIdAndProductCodeAndDomainCode(
            @Nonnull UUID keycloakUserId, @Nonnull String productCode, @Nonnull String domainCode);

    /**
     * Finds a specific topic progress record.
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     * @param topicCode      the topic code
     * @return the topic progress if found
     */
    Optional<TopicProgress> findByKeycloakUserIdAndProductCodeAndTopicCode(
            @Nonnull UUID keycloakUserId, @Nonnull String productCode, @Nonnull String topicCode);

    /**
     * Deletes all progress for a user (GDPR).
     *
     * @param keycloakUserId the user's Keycloak ID
     */
    void deleteByKeycloakUserId(@Nonnull UUID keycloakUserId);
}
