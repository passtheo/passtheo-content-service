package com.passtheo.content.domain.entity;

import com.passtheo.shared.core.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Records an achievement earned by a student.
 */
@Entity
@Table(name = "earned_achievements")
public class EarnedAchievement extends BaseEntity {

    @Column(name = "keycloak_user_id", nullable = false)
    private UUID keycloakUserId;

    @Column(name = "achievement_code", nullable = false, length = 50)
    private String achievementCode;

    @Column(name = "earned_at", nullable = false)
    private Instant earnedAt;

    @Column(name = "trigger_value")
    private Integer triggerValue;

    @Column(name = "notified", nullable = false)
    private boolean notified;

    protected EarnedAchievement() {}

    /**
     * Creates a new earned achievement record.
     *
     * @param keycloakUserId  the user's Keycloak ID
     * @param achievementCode the achievement code
     * @param earnedAt        when the achievement was earned
     * @param triggerValue    the actual value that triggered it
     */
    public EarnedAchievement(UUID keycloakUserId, String achievementCode,
                             Instant earnedAt, Integer triggerValue) {
        this.keycloakUserId = keycloakUserId;
        this.achievementCode = achievementCode;
        this.earnedAt = earnedAt;
        this.triggerValue = triggerValue;
        this.notified = false;
    }

    public UUID getKeycloakUserId() { return keycloakUserId; }
    public void setKeycloakUserId(UUID keycloakUserId) { this.keycloakUserId = keycloakUserId; }
    public String getAchievementCode() { return achievementCode; }
    public void setAchievementCode(String achievementCode) { this.achievementCode = achievementCode; }
    public Instant getEarnedAt() { return earnedAt; }
    public void setEarnedAt(Instant earnedAt) { this.earnedAt = earnedAt; }
    public Integer getTriggerValue() { return triggerValue; }
    public void setTriggerValue(Integer triggerValue) { this.triggerValue = triggerValue; }
    public boolean isNotified() { return notified; }
    public void setNotified(boolean notified) { this.notified = notified; }
}
