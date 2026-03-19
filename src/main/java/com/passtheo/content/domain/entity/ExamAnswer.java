package com.passtheo.content.domain.entity;

import com.passtheo.shared.core.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Records a single answer within an exam attempt.
 */
@Entity
@Table(name = "exam_answers")
public class ExamAnswer extends BaseEntity {

    @Column(name = "exam_attempt_id", nullable = false)
    private UUID examAttemptId;

    @Column(name = "strapi_question_id", nullable = false, length = 100)
    private String strapiQuestionId;

    @Column(name = "question_version", nullable = false)
    private int questionVersion;

    @Column(name = "domain_code", nullable = false, length = 50)
    private String domainCode;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "user_answer", nullable = false, columnDefinition = "jsonb")
    private String userAnswer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "correct_answer", nullable = false, columnDefinition = "jsonb")
    private String correctAnswer;

    @Column(name = "time_taken_ms")
    private Integer timeTakenMs;

    @Column(name = "question_order", nullable = false)
    private int questionOrder;

    @Column(name = "answered_at", nullable = false)
    private Instant answeredAt;

    public ExamAnswer() {}

    public UUID getExamAttemptId() { return examAttemptId; }
    public void setExamAttemptId(UUID examAttemptId) { this.examAttemptId = examAttemptId; }
    public String getStrapiQuestionId() { return strapiQuestionId; }
    public void setStrapiQuestionId(String strapiQuestionId) { this.strapiQuestionId = strapiQuestionId; }
    public int getQuestionVersion() { return questionVersion; }
    public void setQuestionVersion(int questionVersion) { this.questionVersion = questionVersion; }
    public String getDomainCode() { return domainCode; }
    public void setDomainCode(String domainCode) { this.domainCode = domainCode; }
    public boolean isCorrect() { return correct; }
    public void setCorrect(boolean correct) { this.correct = correct; }
    public String getUserAnswer() { return userAnswer; }
    public void setUserAnswer(String userAnswer) { this.userAnswer = userAnswer; }
    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }
    public Integer getTimeTakenMs() { return timeTakenMs; }
    public void setTimeTakenMs(Integer timeTakenMs) { this.timeTakenMs = timeTakenMs; }
    public int getQuestionOrder() { return questionOrder; }
    public void setQuestionOrder(int questionOrder) { this.questionOrder = questionOrder; }
    public Instant getAnsweredAt() { return answeredAt; }
    public void setAnsweredAt(Instant answeredAt) { this.answeredAt = answeredAt; }
}
