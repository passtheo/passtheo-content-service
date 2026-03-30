package com.passtheo.content.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Simplified active practice session for the Flutter dashboard "Continue Practicing" card.
 *
 * @param sessionId        the session UUID
 * @param domainCode       the domain code (e.g. "voorrang")
 * @param domainName       human-readable domain name (e.g. "Voorrang")
 * @param sessionType      the session type (PRACTICE, QUICK_QUIZ, WEAK_REVIEW)
 * @param totalQuestions    total questions in the session
 * @param answeredQuestions number of questions answered so far
 * @param progressPercent  progress as integer percentage (0–100)
 * @param createdAt        when the session was started
 */
public record ActiveSessionDto(
        UUID sessionId,
        String domainCode,
        String domainName,
        String sessionType,
        int totalQuestions,
        int answeredQuestions,
        int progressPercent,
        Instant createdAt
) {
}
