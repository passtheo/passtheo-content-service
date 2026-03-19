package com.passtheo.content.dto.response;

/**
 * Domain progress response DTO.
 */
public record DomainProgressDto(
    String domainCode,
    String domainName,
    int totalQuestions,
    int attemptedCount,
    int correctCount,
    int masteredCount,
    double accuracyPercent,
    double coveragePercent,
    String strength
) {}
