package com.passtheo.content.dto.request;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Request to submit an answer for a question in a practice session.
 * When {@code answer} is {@code null}, the question is treated as skipped:
 * the session advances but no mastery update is applied.
 *
 * @param strapiQuestionId the Strapi question ID
 * @param answer           flexible answer object per interaction type, or null to skip
 * @param timeTakenMs      time taken to answer in milliseconds
 */
public record SubmitAnswerRequest(
    @NotBlank String strapiQuestionId,
    @Nullable Map<String, Object> answer,
    int timeTakenMs
) {}
