package com.passtheo.content.domain.entity;

import com.passtheo.shared.core.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Crowd-sourced difficulty calibration for a question.
 */
@Entity
@Table(name = "question_difficulty")
public class QuestionDifficulty extends BaseEntity {

    @Column(name = "strapi_question_id", nullable = false, length = 100)
    private String strapiQuestionId;

    @Column(name = "product_code", nullable = false, length = 50)
    private String productCode;

    @Column(name = "times_answered", nullable = false)
    private int timesAnswered;

    @Column(name = "times_correct", nullable = false)
    private int timesCorrect;

    @Column(name = "difficulty_score", precision = 5, scale = 2)
    private BigDecimal difficultyScore;

    @Column(name = "calibrated_at")
    private Instant calibratedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected QuestionDifficulty() {}

    public String getStrapiQuestionId() { return strapiQuestionId; }
    public void setStrapiQuestionId(String strapiQuestionId) { this.strapiQuestionId = strapiQuestionId; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public int getTimesAnswered() { return timesAnswered; }
    public void setTimesAnswered(int timesAnswered) { this.timesAnswered = timesAnswered; }
    public int getTimesCorrect() { return timesCorrect; }
    public void setTimesCorrect(int timesCorrect) { this.timesCorrect = timesCorrect; }
    public BigDecimal getDifficultyScore() { return difficultyScore; }
    public void setDifficultyScore(BigDecimal difficultyScore) { this.difficultyScore = difficultyScore; }
    public Instant getCalibratedAt() { return calibratedAt; }
    public void setCalibratedAt(Instant calibratedAt) { this.calibratedAt = calibratedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
