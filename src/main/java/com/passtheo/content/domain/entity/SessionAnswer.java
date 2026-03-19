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
 * Records a single answer within a study session.
 */
@Entity
@Table(name = "session_answers")
public class SessionAnswer extends BaseEntity {

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "keycloak_user_id", nullable = false)
    private UUID keycloakUserId;

    @Column(name = "strapi_question_id", nullable = false, length = 100)
    private String strapiQuestionId;

    @Column(name = "question_version", nullable = false)
    private int questionVersion;

    @Column(name = "interaction_type", nullable = false, length = 30)
    private String interactionType;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "user_answer", nullable = false, columnDefinition = "jsonb")
    private String userAnswer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "correct_answer", nullable = false, columnDefinition = "jsonb")
    private String correctAnswer;

    @Column(name = "time_taken_ms", nullable = false)
    private int timeTakenMs;

    @Column(name = "question_order", nullable = false)
    private int questionOrder;

    @Column(name = "answered_at", nullable = false)
    private Instant answeredAt;

    protected SessionAnswer() {}

    /**
     * Creates a new session answer record.
     *
     * @param sessionId        the session ID
     * @param keycloakUserId   the user's Keycloak ID
     * @param strapiQuestionId the Strapi question ID
     * @param questionVersion  the question version
     * @param interactionType  the interaction type
     * @param correct          whether the answer was correct
     * @param userAnswer       the user's answer as JSON
     * @param correctAnswer    the correct answer as JSON
     * @param timeTakenMs      time taken in milliseconds
     * @param questionOrder    the question order in the session
     */
    public SessionAnswer(UUID sessionId, UUID keycloakUserId, String strapiQuestionId,
                         int questionVersion, String interactionType, boolean correct,
                         String userAnswer, String correctAnswer, int timeTakenMs, int questionOrder) {
        this.sessionId = sessionId;
        this.keycloakUserId = keycloakUserId;
        this.strapiQuestionId = strapiQuestionId;
        this.questionVersion = questionVersion;
        this.interactionType = interactionType;
        this.correct = correct;
        this.userAnswer = userAnswer;
        this.correctAnswer = correctAnswer;
        this.timeTakenMs = timeTakenMs;
        this.questionOrder = questionOrder;
        this.answeredAt = Instant.now();
    }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID sessionId) { this.sessionId = sessionId; }
    public UUID getKeycloakUserId() { return keycloakUserId; }
    public void setKeycloakUserId(UUID keycloakUserId) { this.keycloakUserId = keycloakUserId; }
    public String getStrapiQuestionId() { return strapiQuestionId; }
    public void setStrapiQuestionId(String strapiQuestionId) { this.strapiQuestionId = strapiQuestionId; }
    public int getQuestionVersion() { return questionVersion; }
    public void setQuestionVersion(int questionVersion) { this.questionVersion = questionVersion; }
    public String getInteractionType() { return interactionType; }
    public void setInteractionType(String interactionType) { this.interactionType = interactionType; }
    public boolean isCorrect() { return correct; }
    public void setCorrect(boolean correct) { this.correct = correct; }
    public String getUserAnswer() { return userAnswer; }
    public void setUserAnswer(String userAnswer) { this.userAnswer = userAnswer; }
    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }
    public int getTimeTakenMs() { return timeTakenMs; }
    public void setTimeTakenMs(int timeTakenMs) { this.timeTakenMs = timeTakenMs; }
    public int getQuestionOrder() { return questionOrder; }
    public void setQuestionOrder(int questionOrder) { this.questionOrder = questionOrder; }
    public Instant getAnsweredAt() { return answeredAt; }
    public void setAnsweredAt(Instant answeredAt) { this.answeredAt = answeredAt; }
}
