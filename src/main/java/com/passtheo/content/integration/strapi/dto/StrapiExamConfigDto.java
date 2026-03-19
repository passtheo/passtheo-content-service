package com.passtheo.content.integration.strapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Strapi ExamConfig content type attributes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StrapiExamConfigDto(
    int totalQuestions,
    int timeLimitMinutes,
    int passScore,
    Integer extendedTimeLimitMinutes,
    String questionDistribution,
    boolean shuffleQuestions,
    boolean showFeedbackDuringExam,
    boolean allowPause
) {}
