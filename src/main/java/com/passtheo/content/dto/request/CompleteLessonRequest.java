package com.passtheo.content.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for marking a lesson complete.
 *
 * @param productCode      the product code the lesson belongs to
 * @param topicCode        the topic code the lesson belongs to
 * @param timeSpentSeconds time the user spent reading the lesson
 */
public record CompleteLessonRequest(
        @NotBlank String productCode,
        @NotBlank String topicCode,
        @Min(0) int timeSpentSeconds
) { }
