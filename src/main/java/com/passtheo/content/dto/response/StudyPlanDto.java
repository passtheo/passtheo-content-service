package com.passtheo.content.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Study plan response DTO.
 */
public record StudyPlanDto(
    UUID planId,
    String productCode,
    LocalDate examDate,
    int totalDays,
    int currentDay,
    int dailyQuestionTarget,
    String status,
    List<String> focusDomains,
    List<StudyPlanDayDto> days
) {}
