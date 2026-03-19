package com.passtheo.content.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Exam start response — contains all questions at once (Flutter handles timer).
 */
public record ExamDto(
    UUID examId,
    int totalQuestions,
    int timeLimitSeconds,
    int passScore,
    Instant startedAt,
    Instant expiresAt,
    List<QuestionDto> questions
) {}
