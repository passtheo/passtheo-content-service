package com.passtheo.content.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/**
 * Request to generate a study plan.
 *
 * @param productCode         the product code
 * @param examDate            target exam date (nullable = 30-day default plan)
 * @param dailyQuestionTarget daily question target (default 20)
 */
public record GenerateStudyPlanRequest(
    @NotBlank String productCode,
    LocalDate examDate,
    int dailyQuestionTarget
) {
    public GenerateStudyPlanRequest {
        if (dailyQuestionTarget <= 0) {
            dailyQuestionTarget = 20;
        }
    }
}
