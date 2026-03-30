package com.passtheo.content.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/**
 * Request to generate a study plan.
 *
 * @param productCode         the product code
 * @param examDate            target exam date (nullable = 30-day default plan)
 * @param dailyQuestionTarget daily question target; null = auto-calculate from remaining questions ÷ days
 */
public record GenerateStudyPlanRequest(
    @NotBlank String productCode,
    LocalDate examDate,
    Integer dailyQuestionTarget
) {
    public GenerateStudyPlanRequest {
        if (dailyQuestionTarget != null && dailyQuestionTarget <= 0) {
            dailyQuestionTarget = null;
        }
    }
}
