package com.passtheo.content.domain.entity;

import com.passtheo.content.domain.enums.ExamType;
import com.passtheo.shared.core.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Records a mock exam attempt and its results.
 */
@Entity
@Table(name = "exam_attempts")
public class ExamAttempt extends BaseEntity {

    @Column(name = "keycloak_user_id", nullable = false)
    private UUID keycloakUserId;

    @Column(name = "product_code", nullable = false, length = 50)
    private String productCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "exam_type", nullable = false, length = 20)
    private ExamType examType;

    @Column(name = "total_questions", nullable = false)
    private int totalQuestions;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    @Column(name = "pass_score", nullable = false)
    private int passScore;

    @Column(name = "passed", nullable = false)
    private boolean passed;

    @Column(name = "score_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal scorePercent;

    @Column(name = "time_taken_seconds", nullable = false)
    private int timeTakenSeconds;

    @Column(name = "time_limit_seconds", nullable = false)
    private int timeLimitSeconds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "domain_breakdown", nullable = false, columnDefinition = "jsonb")
    private String domainBreakdown;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    public ExamAttempt() {}

    public UUID getKeycloakUserId() { return keycloakUserId; }
    public void setKeycloakUserId(UUID keycloakUserId) { this.keycloakUserId = keycloakUserId; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public ExamType getExamType() { return examType; }
    public void setExamType(ExamType examType) { this.examType = examType; }
    public int getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(int totalQuestions) { this.totalQuestions = totalQuestions; }
    public int getCorrectCount() { return correctCount; }
    public void setCorrectCount(int correctCount) { this.correctCount = correctCount; }
    public int getPassScore() { return passScore; }
    public void setPassScore(int passScore) { this.passScore = passScore; }
    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }
    public BigDecimal getScorePercent() { return scorePercent; }
    public void setScorePercent(BigDecimal scorePercent) { this.scorePercent = scorePercent; }
    public int getTimeTakenSeconds() { return timeTakenSeconds; }
    public void setTimeTakenSeconds(int timeTakenSeconds) { this.timeTakenSeconds = timeTakenSeconds; }
    public int getTimeLimitSeconds() { return timeLimitSeconds; }
    public void setTimeLimitSeconds(int timeLimitSeconds) { this.timeLimitSeconds = timeLimitSeconds; }
    public String getDomainBreakdown() { return domainBreakdown; }
    public void setDomainBreakdown(String domainBreakdown) { this.domainBreakdown = domainBreakdown; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
