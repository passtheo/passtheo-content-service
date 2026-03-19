package com.passtheo.content.domain.entity;

import com.passtheo.shared.core.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Daily readiness score snapshot for trend tracking.
 */
@Entity
@Table(name = "readiness_snapshots")
public class ReadinessSnapshot extends BaseEntity {

    @Column(name = "keycloak_user_id", nullable = false)
    private UUID keycloakUserId;

    @Column(name = "product_code", nullable = false, length = 50)
    private String productCode;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "readiness_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal readinessScore;

    @Column(name = "coverage_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal coverageScore;

    @Column(name = "accuracy_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal accuracyScore;

    @Column(name = "exam_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal examScore;

    @Column(name = "questions_attempted", nullable = false)
    private int questionsAttempted;

    @Column(name = "total_questions", nullable = false)
    private int totalQuestions;

    @Column(name = "best_exam_score")
    private Integer bestExamScore;

    protected ReadinessSnapshot() {}

    public UUID getKeycloakUserId() { return keycloakUserId; }
    public void setKeycloakUserId(UUID keycloakUserId) { this.keycloakUserId = keycloakUserId; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public LocalDate getSnapshotDate() { return snapshotDate; }
    public void setSnapshotDate(LocalDate snapshotDate) { this.snapshotDate = snapshotDate; }
    public BigDecimal getReadinessScore() { return readinessScore; }
    public void setReadinessScore(BigDecimal readinessScore) { this.readinessScore = readinessScore; }
    public BigDecimal getCoverageScore() { return coverageScore; }
    public void setCoverageScore(BigDecimal coverageScore) { this.coverageScore = coverageScore; }
    public BigDecimal getAccuracyScore() { return accuracyScore; }
    public void setAccuracyScore(BigDecimal accuracyScore) { this.accuracyScore = accuracyScore; }
    public BigDecimal getExamScore() { return examScore; }
    public void setExamScore(BigDecimal examScore) { this.examScore = examScore; }
    public int getQuestionsAttempted() { return questionsAttempted; }
    public void setQuestionsAttempted(int questionsAttempted) { this.questionsAttempted = questionsAttempted; }
    public int getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(int totalQuestions) { this.totalQuestions = totalQuestions; }
    public Integer getBestExamScore() { return bestExamScore; }
    public void setBestExamScore(Integer bestExamScore) { this.bestExamScore = bestExamScore; }
}
