package com.passtheo.content.dto.response;

import java.time.LocalDate;

/**
 * Study plan day response DTO.
 */
public record StudyPlanDayDto(
    int dayNumber,
    LocalDate planDate,
    String domainCode,
    String domainName,
    int questionTarget,
    int questionsCompleted,
    boolean includeExam,
    String status,
    String message
) {}
