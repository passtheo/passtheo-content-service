package com.passtheo.content.domain.entity;

import com.passtheo.content.domain.enums.PlanStatus;
import com.passtheo.shared.core.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A generated study plan for a student targeting an exam date.
 */
@Entity
@Table(name = "study_plans")
public class StudyPlan extends BaseEntity {

    @Column(name = "keycloak_user_id", nullable = false)
    private UUID keycloakUserId;

    @Column(name = "product_code", nullable = false, length = 50)
    private String productCode;

    @Column(name = "exam_date")
    private LocalDate examDate;

    @Column(name = "total_days", nullable = false)
    private int totalDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PlanStatus status;

    @Column(name = "daily_question_target", nullable = false)
    private int dailyQuestionTarget;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "focus_domains", columnDefinition = "jsonb")
    private String focusDomains;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public StudyPlan() {}

    public UUID getKeycloakUserId() { return keycloakUserId; }
    public void setKeycloakUserId(UUID keycloakUserId) { this.keycloakUserId = keycloakUserId; }
    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public LocalDate getExamDate() { return examDate; }
    public void setExamDate(LocalDate examDate) { this.examDate = examDate; }
    public int getTotalDays() { return totalDays; }
    public void setTotalDays(int totalDays) { this.totalDays = totalDays; }
    public PlanStatus getStatus() { return status; }
    public void setStatus(PlanStatus status) { this.status = status; }
    public int getDailyQuestionTarget() { return dailyQuestionTarget; }
    public void setDailyQuestionTarget(int dailyQuestionTarget) { this.dailyQuestionTarget = dailyQuestionTarget; }
    public String getFocusDomains() { return focusDomains; }
    public void setFocusDomains(String focusDomains) { this.focusDomains = focusDomains; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
