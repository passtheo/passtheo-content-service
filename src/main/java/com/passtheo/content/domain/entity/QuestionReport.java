package com.passtheo.content.domain.entity;

import com.passtheo.content.domain.enums.ReportType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Records a user-reported error on a question.
 */
@Entity
@Table(name = "question_reports")
public class QuestionReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "keycloak_user_id", nullable = false)
    private UUID keycloakUserId;

    @Column(name = "strapi_question_id", nullable = false, length = 100)
    private String strapiQuestionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 50)
    private ReportType reportType;

    @Column(name = "comment")
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected QuestionReport() {}

    /**
     * Creates a new question report.
     *
     * @param tenantId         the tenant ID
     * @param keycloakUserId   the user's Keycloak ID
     * @param strapiQuestionId the Strapi question ID
     * @param reportType       the type of report
     * @param comment          optional user comment
     */
    public QuestionReport(UUID tenantId, UUID keycloakUserId, String strapiQuestionId,
                          ReportType reportType, String comment) {
        this.tenantId = tenantId;
        this.keycloakUserId = keycloakUserId;
        this.strapiQuestionId = strapiQuestionId;
        this.reportType = reportType;
        this.comment = comment;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public UUID getKeycloakUserId() { return keycloakUserId; }
    public void setKeycloakUserId(UUID keycloakUserId) { this.keycloakUserId = keycloakUserId; }
    public String getStrapiQuestionId() { return strapiQuestionId; }
    public void setStrapiQuestionId(String strapiQuestionId) { this.strapiQuestionId = strapiQuestionId; }
    public ReportType getReportType() { return reportType; }
    public void setReportType(ReportType reportType) { this.reportType = reportType; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Instant getCreatedAt() { return createdAt; }
}
