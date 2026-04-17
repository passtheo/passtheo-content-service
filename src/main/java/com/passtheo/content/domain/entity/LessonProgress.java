package com.passtheo.content.domain.entity;

import com.passtheo.shared.core.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks a user's reading progress through a lesson. Created lazily on first
 * completion call. Uncomplete sets {@code isCompleted=false} but preserves
 * {@code startedAt}, {@code completedAt}, and {@code timeSpentSeconds} for
 * analytics — no XP refund.
 */
@Entity
@Table(name = "lesson_progress")
public class LessonProgress extends BaseEntity {

    @Column(name = "keycloak_user_id", nullable = false)
    private UUID keycloakUserId;

    @Column(name = "product_code", nullable = false, length = 50)
    private String productCode;

    @Column(name = "topic_code", nullable = false, length = 50)
    private String topicCode;

    @Column(name = "lesson_slug", nullable = false, length = 100)
    private String lessonSlug;

    @Column(name = "is_completed", nullable = false)
    private boolean completed;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "time_spent_seconds", nullable = false)
    private int timeSpentSeconds;

    @Column(name = "last_scroll_position", nullable = false)
    private int lastScrollPosition;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    protected LessonProgress() { }

    /**
     * Creates a new lesson progress record.
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     * @param topicCode      the topic code
     * @param lessonSlug     the lesson slug
     */
    public LessonProgress(UUID keycloakUserId, String productCode, String topicCode, String lessonSlug) {
        this.keycloakUserId = keycloakUserId;
        this.productCode = productCode;
        this.topicCode = topicCode;
        this.lessonSlug = lessonSlug;
        this.completed = false;
        this.startedAt = Instant.now();
        this.timeSpentSeconds = 0;
        this.lastScrollPosition = 0;
    }

    public UUID getKeycloakUserId() {
        return keycloakUserId;
    }

    public String getProductCode() {
        return productCode;
    }

    public String getTopicCode() {
        return topicCode;
    }

    public String getLessonSlug() {
        return lessonSlug;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public int getTimeSpentSeconds() {
        return timeSpentSeconds;
    }

    public void setTimeSpentSeconds(int timeSpentSeconds) {
        this.timeSpentSeconds = timeSpentSeconds;
    }

    public int getLastScrollPosition() {
        return lastScrollPosition;
    }

    public void setLastScrollPosition(int lastScrollPosition) {
        this.lastScrollPosition = lastScrollPosition;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }
}
