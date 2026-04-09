package com.passtheo.content.dto.response;

import java.util.List;

/**
 * Lightweight exam configuration preview for the intro screen.
 * Contains only the fields needed for display — no internal config like domain weights.
 */
public record ExamConfigPreviewDto(
    String title,
    String description,
    int totalQuestions,
    int timeLimitMinutes,
    int passScore,
    List<String> rules
) {}
