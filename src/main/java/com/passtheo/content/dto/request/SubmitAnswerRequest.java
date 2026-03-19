package com.passtheo.content.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Request to submit an answer for a question in a practice session.
 *
 * @param strapiQuestionId the Strapi question ID
 * @param answer           flexible answer object per interaction type
 * @param timeTakenMs      time taken to answer in milliseconds
 */
public record SubmitAnswerRequest(
    @NotBlank String strapiQuestionId,
    @NotNull Map<String, Object> answer,
    int timeTakenMs
) {}
