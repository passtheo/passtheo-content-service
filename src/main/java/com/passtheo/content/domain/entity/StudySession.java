package com.passtheo.content.domain.entity;

import com.passtheo.content.domain.enums.SessionStatus;
import com.passtheo.content.domain.enums.SessionType;
import com.passtheo.shared.core.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Represents a practice study session for a student.
 */
@Entity
@Table(name = "study_sessions")
public class StudySession extends BaseEntity {

    @Column(name = "keycloak_user_id", nullable = false)
    private UUID keycloakUserId;

    @Column(name = "product_code", nullable = false, length = 50)
    private String productCode;

    @Column(name = "domain_code", length = 50)
    private String domainCode;

    @Column(name = "topic_code", length = 50)
    private String topicCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false, length = 20)
    private SessionType sessionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SessionStatus status;

    @Column(name = "total_questions", nullable = false)
    private int totalQuestions;

    @Column(name = "answered_count", nullable = false)
    private int answeredCount;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    @Column(name = "accuracy_percent", precision = 5, scale = 2)
    private BigDecimal accuracyPercent;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "time_spent_seconds", nullable = false)
    private int timeSpentSeconds;

    /**
     * Content locale for this session (e.g. "nl", "en"). Set once at creation
     * from the start-session request and never changed. All Strapi fetches
     * during this session use this locale.
     */
    @Column(name = "locale", nullable = false, length = 10)
    private String locale;

    /**
     * Comma-separated Strapi question document IDs in the order they were selected
     * at session start. Null for sessions created before V9 migration.
     */
    @Column(name = "question_ids", columnDefinition = "text")
    private String questionIds;

    protected StudySession() {}

    /**
     * Creates a new study session.
     *
     * @param keycloakUserId the user's Keycloak ID
     * @param productCode    the product code
     * @param domainCode     the domain code (nullable for mixed)
     * @param topicCode      the topic code (nullable for all topics)
     * @param sessionType    the session type
     * @param totalQuestions the total number of questions
     * @param locale         the content locale for this session (e.g. "nl", "en")
     */
    public StudySession(UUID keycloakUserId, String productCode, String domainCode,
                        String topicCode, SessionType sessionType, int totalQuestions,
                        String locale) {
        this.keycloakUserId = keycloakUserId;
        this.productCode = productCode;
        this.domainCode = domainCode;
        this.topicCode = topicCode;
        this.sessionType = sessionType;
        this.status = SessionStatus.IN_PROGRESS;
        this.totalQuestions = totalQuestions;
        this.answeredCount = 0;
        this.correctCount = 0;
        this.startedAt = Instant.now();
        this.lastActivityAt = Instant.now();
        this.timeSpentSeconds = 0;
        this.locale = locale;
    }

    public UUID getKeycloakUserId() { return keycloakUserId; }
    public void setKeycloakUserId(UUID keycloakUserId) { this.keycloakUserId = keycloakUserId; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getDomainCode() { return domainCode; }
    public void setDomainCode(String domainCode) { this.domainCode = domainCode; }
    public String getTopicCode() { return topicCode; }
    public void setTopicCode(String topicCode) { this.topicCode = topicCode; }
    public SessionType getSessionType() { return sessionType; }
    public void setSessionType(SessionType sessionType) { this.sessionType = sessionType; }
    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus status) { this.status = status; }
    public int getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(int totalQuestions) { this.totalQuestions = totalQuestions; }
    public int getAnsweredCount() { return answeredCount; }
    public void setAnsweredCount(int answeredCount) { this.answeredCount = answeredCount; }
    public int getCorrectCount() { return correctCount; }
    public void setCorrectCount(int correctCount) { this.correctCount = correctCount; }
    public BigDecimal getAccuracyPercent() { return accuracyPercent; }
    public void setAccuracyPercent(BigDecimal accuracyPercent) { this.accuracyPercent = accuracyPercent; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
    public Instant getLastActivityAt() { return lastActivityAt; }
    public void setLastActivityAt(Instant lastActivityAt) { this.lastActivityAt = lastActivityAt; }
    public int getTimeSpentSeconds() { return timeSpentSeconds; }
    public void setTimeSpentSeconds(int timeSpentSeconds) { this.timeSpentSeconds = timeSpentSeconds; }
    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }

    /**
     * Returns the ordered question IDs stored at session start.
     * Returns an empty list for legacy sessions (null column).
     *
     * @return immutable list of Strapi question document IDs
     */
    public List<String> getQuestionIdList() {
        if (questionIds == null || questionIds.isBlank()) {
            return java.util.List.of();
        }
        return java.util.List.of(questionIds.split(",", -1));
    }

    /**
     * Stores the ordered question IDs as a comma-separated string.
     *
     * @param ids the ordered question document IDs
     */
    public void setQuestionIdList(List<String> ids) {
        this.questionIds = (ids == null || ids.isEmpty()) ? null : String.join(",", ids);
    }
}
