package com.passtheo.content.dto.response;

import java.util.List;

/**
 * Readiness score response DTO.
 */
public record ReadinessDto(
    double readinessScore,
    double coverageScore,
    double accuracyScore,
    double examScore,
    String readinessLabel,
    int questionsAttempted,
    int totalQuestions,
    Integer bestExamScore,
    int examPassScore,
    List<DomainStrengthDto> domainStrengths
) {
    /**
     * Per-domain strength breakdown.
     */
    public record DomainStrengthDto(
        String domainCode,
        String domainName,
        double accuracyPercent,
        double coveragePercent,
        String strength
    ) {}
}
