package com.passtheo.content.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

/**
 * Request to submit all exam answers at once.
 *
 * @param answers list of exam answers
 */
public record SubmitExamRequest(
    @NotEmpty List<ExamAnswerItem> answers
) {

    /**
     * A single exam answer.
     *
     * @param strapiQuestionId the question ID
     * @param answer           the answer object
     * @param timeTakenMs      time taken (nullable)
     */
    public record ExamAnswerItem(
        String strapiQuestionId,
        Map<String, Object> answer,
        Integer timeTakenMs
    ) {}
}
