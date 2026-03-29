package com.passtheo.content.domain.entity;

import com.passtheo.shared.core.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Tracks daily study streaks and freeze slots for a student per product.
 */
@Entity
@Table(name = "streaks")
public class Streak extends BaseEntity {

    @Column(name = "keycloak_user_id", nullable = false)
    private UUID keycloakUserId;

    @Column(name = "product_code", nullable = false, length = 50)
    private String productCode;

    @Column(name = "current_streak", nullable = false)
    private int currentStreak;

    @Column(name = "longest_streak", nullable = false)
    private int longestStreak;

    @Column(name = "last_study_date")
    private LocalDate lastStudyDate;

    @Column(name = "freeze_slots_available", nullable = false)
    private int freezeSlotsAvailable;

    @Column(name = "freeze_slots_used", nullable = false)
    private int freezeSlotsUsed;

    @Column(name = "total_study_days", nullable = false)
    private int totalStudyDays;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Streak() {}

    /**
     * Creates a new streak record for a user/product.
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     */
    public Streak(UUID keycloakUserId, String productCode) {
        this.keycloakUserId = keycloakUserId;
        this.productCode = productCode;
        this.currentStreak = 0;
        this.longestStreak = 0;
        this.freezeSlotsAvailable = 0;
        this.freezeSlotsUsed = 0;
        this.totalStudyDays = 0;
        this.updatedAt = Instant.now();
    }

    public UUID getKeycloakUserId() { return keycloakUserId; }
    public void setKeycloakUserId(UUID keycloakUserId) { this.keycloakUserId = keycloakUserId; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public int getCurrentStreak() { return currentStreak; }
    public void setCurrentStreak(int currentStreak) { this.currentStreak = currentStreak; }
    public int getLongestStreak() { return longestStreak; }
    public void setLongestStreak(int longestStreak) { this.longestStreak = longestStreak; }
    public LocalDate getLastStudyDate() { return lastStudyDate; }
    public void setLastStudyDate(LocalDate lastStudyDate) { this.lastStudyDate = lastStudyDate; }
    public int getFreezeSlotsAvailable() { return freezeSlotsAvailable; }
    public void setFreezeSlotsAvailable(int freezeSlotsAvailable) { this.freezeSlotsAvailable = freezeSlotsAvailable; }
    public int getFreezeSlotsUsed() { return freezeSlotsUsed; }
    public void setFreezeSlotsUsed(int freezeSlotsUsed) { this.freezeSlotsUsed = freezeSlotsUsed; }
    public int getTotalStudyDays() { return totalStudyDays; }
    public void setTotalStudyDays(int totalStudyDays) { this.totalStudyDays = totalStudyDays; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
