package com.passtheo.content.dto.response;

import java.time.LocalDate;

/**
 * Daily readiness snapshot for trend charts.
 */
public record ReadinessSnapshotDto(
    LocalDate date,
    double readinessScore,
    double coverageScore,
    double accuracyScore
) {}
