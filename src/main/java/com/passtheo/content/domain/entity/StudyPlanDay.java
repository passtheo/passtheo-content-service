package com.passtheo.content.domain.entity;

import com.passtheo.content.domain.enums.PlanDayStatus;
import com.passtheo.shared.core.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A single day within a study plan.
 */
@Entity
@Table(name = "study_plan_days")
public class StudyPlanDay extends BaseEntity {

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    @Column(name = "plan_date", nullable = false)
    private LocalDate planDate;

    @Column(name = "domain_code", nullable = false, length = 50)
    private String domainCode;

    @Column(name = "question_target", nullable = false)
    private int questionTarget;

    @Column(name = "questions_completed", nullable = false)
    private int questionsCompleted;

    @Column(name = "include_exam", nullable = false)
    private boolean includeExam;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PlanDayStatus status;

    @Column(name = "completed_at")
    private Instant completedAt;

    public StudyPlanDay() {}

    public UUID getPlanId() { return planId; }
    public void setPlanId(UUID planId) { this.planId = planId; }
    public int getDayNumber() { return dayNumber; }
    public void setDayNumber(int dayNumber) { this.dayNumber = dayNumber; }
    public LocalDate getPlanDate() { return planDate; }
    public void setPlanDate(LocalDate planDate) { this.planDate = planDate; }
    public String getDomainCode() { return domainCode; }
    public void setDomainCode(String domainCode) { this.domainCode = domainCode; }
    public int getQuestionTarget() { return questionTarget; }
    public void setQuestionTarget(int questionTarget) { this.questionTarget = questionTarget; }
    public int getQuestionsCompleted() { return questionsCompleted; }
    public void setQuestionsCompleted(int questionsCompleted) { this.questionsCompleted = questionsCompleted; }
    public boolean isIncludeExam() { return includeExam; }
    public void setIncludeExam(boolean includeExam) { this.includeExam = includeExam; }
    public PlanDayStatus getStatus() { return status; }
    public void setStatus(PlanDayStatus status) { this.status = status; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
