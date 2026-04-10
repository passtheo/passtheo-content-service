package com.passtheo.content.integration.strapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * Strapi ExamConfig content type attributes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StrapiExamConfigDto(
    int id,
    String documentId,
    String title,
    String description,
    List<String> rules,
    int totalQuestions,
    int timeLimitMinutes,
    int passScore,
    Integer extendedTimeLimitMinutes,
    String questionDistribution,
    boolean shuffleQuestions,
    boolean showFeedbackDuringExam,
    List<DomainWeightDto> domainWeights,
    Map<String, Double> difficultyDistribution,
    boolean allowPause
) {

    /**
     * Per-domain question weight configuration from Strapi ExamConfig.
     * targetQuestions is the ideal count; minQuestions is the floor if the pool is small.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DomainWeightDto(
        String domainCode,
        int targetQuestions,
        int minQuestions
    ) {}
}
