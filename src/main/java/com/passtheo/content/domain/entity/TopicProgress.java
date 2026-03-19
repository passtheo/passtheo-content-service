package com.passtheo.content.domain.entity;

import com.passtheo.shared.core.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Aggregated progress per topic for a student.
 */
@Entity
@Table(name = "topic_progress")
public class TopicProgress extends BaseEntity {

    @Column(name = "keycloak_user_id", nullable = false)
    private UUID keycloakUserId;

    @Column(name = "product_code", nullable = false, length = 50)
    private String productCode;

    @Column(name = "domain_code", nullable = false, length = 50)
    private String domainCode;

    @Column(name = "topic_code", nullable = false, length = 50)
    private String topicCode;

    @Column(name = "total_questions", nullable = false)
    private int totalQuestions;

    @Column(name = "attempted_count", nullable = false)
    private int attemptedCount;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    @Column(name = "mastered_count", nullable = false)
    private int masteredCount;

    @Column(name = "accuracy_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal accuracyPercent;

    @Column(name = "coverage_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal coveragePercent;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TopicProgress() {}

    public UUID getKeycloakUserId() { return keycloakUserId; }
    public void setKeycloakUserId(UUID keycloakUserId) { this.keycloakUserId = keycloakUserId; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getDomainCode() { return domainCode; }
    public void setDomainCode(String domainCode) { this.domainCode = domainCode; }
    public String getTopicCode() { return topicCode; }
    public void setTopicCode(String topicCode) { this.topicCode = topicCode; }
    public int getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(int totalQuestions) { this.totalQuestions = totalQuestions; }
    public int getAttemptedCount() { return attemptedCount; }
    public void setAttemptedCount(int attemptedCount) { this.attemptedCount = attemptedCount; }
    public int getCorrectCount() { return correctCount; }
    public void setCorrectCount(int correctCount) { this.correctCount = correctCount; }
    public int getMasteredCount() { return masteredCount; }
    public void setMasteredCount(int masteredCount) { this.masteredCount = masteredCount; }
    public BigDecimal getAccuracyPercent() { return accuracyPercent; }
    public void setAccuracyPercent(BigDecimal accuracyPercent) { this.accuracyPercent = accuracyPercent; }
    public BigDecimal getCoveragePercent() { return coveragePercent; }
    public void setCoveragePercent(BigDecimal coveragePercent) { this.coveragePercent = coveragePercent; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
