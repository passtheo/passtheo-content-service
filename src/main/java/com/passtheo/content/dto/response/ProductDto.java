package com.passtheo.content.dto.response;

/**
 * Product response DTO.
 */
public record ProductDto(
    String code,
    String name,
    String licenceCode,
    String description,
    String icon,
    ExamConfigDto examConfig,
    int domainCount,
    int totalQuestions
) {
    /**
     * Exam config nested DTO.
     */
    public record ExamConfigDto(
        int totalQuestions,
        int timeLimitMinutes,
        int passScore
    ) {}
}
