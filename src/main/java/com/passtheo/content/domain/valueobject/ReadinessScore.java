package com.passtheo.content.domain.valueobject;

import com.passtheo.content.domain.enums.ReadinessLabel;

import java.util.List;

/**
 * Composite readiness score for a student.
 *
 * @param readinessScore     composite score 0-100
 * @param coverageScore      coverage component 0-100
 * @param accuracyScore      accuracy component 0-100
 * @param examScore          exam component 0-100
 * @param readinessLabel     human-readable label
 * @param questionsAttempted total questions attempted
 * @param totalQuestions     total questions available
 * @param bestExamScore      best mock exam correct count (nullable)
 * @param examPassScore      pass score threshold
 * @param domainStrengths    per-domain strength breakdown
 * @param examCountdownDays  days until scheduled exam (nullable if no exam date)
 * @param predictedReadyDate predicted date at which readiness reaches 80% (nullable)
 */
public record ReadinessScore(
    double readinessScore,
    double coverageScore,
    double accuracyScore,
    double examScore,
    ReadinessLabel readinessLabel,
    int questionsAttempted,
    int totalQuestions,
    Integer bestExamScore,
    int examPassScore,
    List<DomainStrengthValue> domainStrengths,
    Integer examCountdownDays,
    java.time.LocalDate predictedReadyDate
) {

    /**
     * Per-domain strength breakdown.
     *
     * @param domainCode      the domain code
     * @param domainName      the domain display name
     * @param accuracyPercent accuracy percentage
     * @param coveragePercent coverage percentage
     * @param strength        strength classification
     */
    public record DomainStrengthValue(
        String domainCode,
        String domainName,
        double accuracyPercent,
        double coveragePercent,
        String strength
    ) {}
}
