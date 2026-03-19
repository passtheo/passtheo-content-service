package com.passtheo.content.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request to start a new practice session.
 *
 * @param productCode   the product code (e.g. "auto-b")
 * @param domainCode    the domain code (nullable = mixed from all domains)
 * @param topicCode     the topic code (nullable = all topics in domain)
 * @param sessionType   session type: PRACTICE, QUICK_QUIZ, WEAK_REVIEW
 * @param questionCount number of questions (5-50, default 10)
 * @param locale        content locale (default "nl")
 */
public record StartSessionRequest(
    @NotBlank String productCode,
    String domainCode,
    String topicCode,
    @NotNull String sessionType,
    @Min(1) @Max(50) int questionCount,
    String locale
) {
    public StartSessionRequest {
        if (locale == null || locale.isBlank()) {
            locale = "nl";
        }
        if (questionCount == 0) {
            questionCount = 10;
        }
    }
}
