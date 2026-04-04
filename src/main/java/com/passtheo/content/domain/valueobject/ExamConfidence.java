package com.passtheo.content.domain.valueobject;

/**
 * Composite exam confidence score (0-95) computed from 5 criteria.
 *
 * @param score              total confidence score (0-95, capped)
 * @param label              human-readable label (NOT_STARTED, NOT_READY, GETTING_THERE, ALMOST_READY, READY)
 * @param recommendation     actionable recommendation text
 * @param breakdown          per-criterion point breakdown
 */
public record ExamConfidence(
    int score,
    String label,
    String recommendation,
    Breakdown breakdown
) {

    /**
     * Per-criterion point breakdown.
     *
     * @param coveragePoints        coverage criterion points (max 20)
     * @param accuracyPoints        accuracy criterion points (max 25)
     * @param examConsistencyPoints mock exam consistency points (max 30)
     * @param avgScorePoints        average exam score points (max 15)
     * @param noWeakDomainsPoints   no-weak-domains bonus points (max 10)
     * @param coverageMet           whether the coverage threshold is met
     * @param accuracyMet           whether the accuracy threshold is met
     * @param consecutivePasses     number of consecutive recent exam passes
     * @param weakDomains           number of domains classified as WEAK
     */
    public record Breakdown(
        int coveragePoints,
        int accuracyPoints,
        int examConsistencyPoints,
        int avgScorePoints,
        int noWeakDomainsPoints,
        boolean coverageMet,
        boolean accuracyMet,
        int consecutivePasses,
        int weakDomains
    ) { }
}
