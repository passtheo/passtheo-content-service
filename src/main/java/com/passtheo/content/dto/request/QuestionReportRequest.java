package com.passtheo.content.dto.request;

import com.passtheo.content.domain.enums.ReportType;
import jakarta.annotation.Nonnull;

/**
 * Request to report an error in a question.
 */
public record QuestionReportRequest(
    @Nonnull ReportType reportType,
    String comment
) {}
