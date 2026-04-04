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
    List<DomainStrengthDto> domainStrengths,
    int examConfidence,
    String examConfidenceLabel,
    String recommendation,
    ConfidenceBreakdownDto confidenceBreakdown
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

    /**
     * Per-criterion point breakdown for exam confidence.
     */
    public record ConfidenceBreakdownDto(
        int coveragePoints,
        int accuracyPoints,
        int examConsistencyPoints,
        int avgScorePoints,
        int noWeakDomainsPoints,
        boolean coverageMet,
        boolean accuracyMet,
        int consecutivePasses,
        List<String> weakDomainCodes
    ) {}
}
