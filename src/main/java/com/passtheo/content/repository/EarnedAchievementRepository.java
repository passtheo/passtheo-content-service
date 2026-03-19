package com.passtheo.content.repository;

import com.passtheo.content.domain.entity.EarnedAchievement;
import jakarta.annotation.Nonnull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Repository for EarnedAchievement entities.
 */
@Repository
public interface EarnedAchievementRepository extends JpaRepository<EarnedAchievement, UUID> {

    /**
     * Finds all earned achievements for a user.
     *
     * @param keycloakUserId the user's Keycloak ID
     * @return list of earned achievements
     */
    List<EarnedAchievement> findByKeycloakUserId(@Nonnull UUID keycloakUserId);

    /**
     * Finds all earned achievement codes for a user.
     *
     * @param keycloakUserId the user's Keycloak ID
     * @return set of earned achievement codes
     */
    @Query("SELECT ea.achievementCode FROM EarnedAchievement ea WHERE ea.keycloakUserId = :userId")
    Set<String> findEarnedCodes(@Param("userId") UUID keycloakUserId);

    /**
     * Deletes all achievements for a user (GDPR).
     *
     * @param keycloakUserId the user's Keycloak ID
     */
    void deleteByKeycloakUserId(@Nonnull UUID keycloakUserId);
}
