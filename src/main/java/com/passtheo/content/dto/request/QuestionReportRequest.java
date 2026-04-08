package com.passtheo.content.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to report an error in a question.
 */
public record QuestionReportRequest(
    @NotBlank String reportType,
    String comment
) {}
