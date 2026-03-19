package com.passtheo.content.domain.entity;

import com.passtheo.content.domain.enums.MasteryLevel;
import com.passtheo.shared.core.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Tracks per-question spaced repetition state for a student.
 */
@Entity
@Table(name = "question_progress")
public class QuestionProgress extends BaseEntity {

    @Column(name = "keycloak_user_id", nullable = false)
    private UUID keycloakUserId;

    @Column(name = "strapi_question_id", nullable = false, length = 100)
    private String strapiQuestionId;

    @Column(name = "product_code", nullable = false, length = 50)
    private String productCode;

    @Column(name = "domain_code", nullable = false, length = 50)
    private String domainCode;

    @Column(name = "topic_code", nullable = false, length = 50)
    private String topicCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "mastery_level", nullable = false, length = 20)
    private MasteryLevel masteryLevel;

    @Column(name = "ease_factor", nullable = false, precision = 4, scale = 2)
    private BigDecimal easeFactor;

    @Column(name = "consecutive_correct", nullable = false)
    private int consecutiveCorrect;

    @Column(name = "total_attempts", nullable = false)
    private int totalAttempts;

    @Column(name = "total_correct", nullable = false)
    private int totalCorrect;

    @Column(name = "last_answered_at")
    private Instant lastAnsweredAt;

    @Column(name = "next_review_at")
    private Instant nextReviewAt;

    @Column(name = "interval_days", nullable = false)
    private int intervalDays;

    protected QuestionProgress() {}

    /**
     * Creates a new question progress record (initial NEW state).
     *
     * @param keycloakUserId   the user's Keycloak ID
     * @param strapiQuestionId the Strapi question ID
     * @param productCode      the product code
     * @param domainCode       the domain code
     * @param topicCode        the topic code
     */
    public QuestionProgress(UUID keycloakUserId, String strapiQuestionId,
                            String productCode, String domainCode, String topicCode) {
        this.keycloakUserId = keycloakUserId;
        this.strapiQuestionId = strapiQuestionId;
        this.productCode = productCode;
        this.domainCode = domainCode;
        this.topicCode = topicCode;
        this.masteryLevel = MasteryLevel.NEW;
        this.easeFactor = new BigDecimal("2.50");
        this.consecutiveCorrect = 0;
        this.totalAttempts = 0;
        this.totalCorrect = 0;
        this.intervalDays = 0;
    }

    public UUID getKeycloakUserId() { return keycloakUserId; }
    public void setKeycloakUserId(UUID keycloakUserId) { this.keycloakUserId = keycloakUserId; }
    public String getStrapiQuestionId() { return strapiQuestionId; }
    public void setStrapiQuestionId(String strapiQuestionId) { this.strapiQuestionId = strapiQuestionId; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getDomainCode() { return domainCode; }
    public void setDomainCode(String domainCode) { this.domainCode = domainCode; }
    public String getTopicCode() { return topicCode; }
    public void setTopicCode(String topicCode) { this.topicCode = topicCode; }
    public MasteryLevel getMasteryLevel() { return masteryLevel; }
    public void setMasteryLevel(MasteryLevel masteryLevel) { this.masteryLevel = masteryLevel; }
    public BigDecimal getEaseFactor() { return easeFactor; }
    public void setEaseFactor(BigDecimal easeFactor) { this.easeFactor = easeFactor; }
    public int getConsecutiveCorrect() { return consecutiveCorrect; }
    public void setConsecutiveCorrect(int consecutiveCorrect) { this.consecutiveCorrect = consecutiveCorrect; }
    public int getTotalAttempts() { return totalAttempts; }
    public void setTotalAttempts(int totalAttempts) { this.totalAttempts = totalAttempts; }
    public int getTotalCorrect() { return totalCorrect; }
    public void setTotalCorrect(int totalCorrect) { this.totalCorrect = totalCorrect; }
    public Instant getLastAnsweredAt() { return lastAnsweredAt; }
    public void setLastAnsweredAt(Instant lastAnsweredAt) { this.lastAnsweredAt = lastAnsweredAt; }
    public Instant getNextReviewAt() { return nextReviewAt; }
    public void setNextReviewAt(Instant nextReviewAt) { this.nextReviewAt = nextReviewAt; }
    public int getIntervalDays() { return intervalDays; }
    public void setIntervalDays(int intervalDays) { this.intervalDays = intervalDays; }
}
