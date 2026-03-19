package com.passtheo.content.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to start a mock exam.
 *
 * @param productCode the product code (e.g. "auto-b")
 * @param locale      content locale (default "nl")
 */
public record StartExamRequest(
    @NotBlank String productCode,
    String locale
) {
    public StartExamRequest {
        if (locale == null || locale.isBlank()) {
            locale = "nl";
        }
    }
}
