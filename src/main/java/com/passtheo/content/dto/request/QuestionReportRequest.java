package com.passtheo.content.dto.request;

import com.passtheo.content.domain.enums.ReportType;
import jakarta.validation.constraints.NotNull;

/**
 * Request to report an error in a question.
 *
 * @param reportType the type of report (INCORRECT_ANSWER, UNCLEAR_QUESTION, WRONG_IMAGE, WRONG_EXPLANATION, OTHER)
 * @param comment optional comment with additional details
 */
public record QuestionReportRequest(
    @NotNull ReportType reportType,
    String comment
) {}
