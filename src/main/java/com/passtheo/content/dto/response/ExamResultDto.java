package com.passtheo.content.dto.response;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Exam submission result response DTO.
 */
public record ExamResultDto(
    UUID examId,
    boolean passed,
    int correctCount,
    int totalQuestions,
    int passScore,
    double scorePercent,
    int timeTakenSeconds,
    List<DomainBreakdownDto> domainBreakdown,
    List<WrongAnswerDto> wrongAnswers,
    ReadinessUpdateDto readinessUpdate,
    List<EarnedAchievementDto> newAchievements,
    StreakUpdateDto streakUpdate,
    XpUpdateDto xpUpdate
) {
    /**
     * Per-domain exam breakdown.
     */
    public record DomainBreakdownDto(
        String domainCode,
        String domainName,
        int correct,
        int total,
        double accuracyPercent
    ) {}

    /**
     * Wrong answer with explanation for review.
     */
    public record WrongAnswerDto(
        String strapiQuestionId,
        String questionText,
        Map<String, Object> yourAnswer,
        Map<String, Object> correctAnswer,
        AnswerResultDto.ExplanationDto explanation
    ) {}

    /**
     * Readiness score update after exam.
     */
    public record ReadinessUpdateDto(
        double previousScore,
        double newScore,
        String label
    ) {}

}
