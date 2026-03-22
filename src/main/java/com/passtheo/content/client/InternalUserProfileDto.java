package com.passtheo.content.client;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Internal DTO received from user-service GET /internal/users/{keycloakUserId}/profile.
 */
public record InternalUserProfileDto(
        UUID keycloakUserId,
        String email,
        boolean emailVerified,
        Instant emailVerifiedAt,
        LocalDate examDate,
        UUID tenantId
) {}
