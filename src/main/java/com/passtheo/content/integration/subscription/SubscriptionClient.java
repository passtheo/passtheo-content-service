package com.passtheo.content.integration.subscription;

import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;

/**
 * WebClient for subscription-service internal API calls.
 * Used to increment usage counters and check weekly exam limits for free users.
 */
@Component
public class SubscriptionClient {

    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionClient.class);

    private final WebClient webClient;

    /**
     * Constructs the subscription client.
     *
     * @param subscriptionWebClient the pre-configured subscription-service WebClient
     */
    public SubscriptionClient(@Qualifier("subscriptionWebClient") WebClient subscriptionWebClient) {
        this.webClient = subscriptionWebClient;
    }

    /**
     * Increments the daily question usage counter for a free user.
     *
     * @param tenantId the tenant ID
     * @param userId   the user's Keycloak ID
     * @return true if usage increment was allowed, false if daily limit reached
     */
    public boolean incrementQuestionUsage(@Nonnull UUID tenantId, @Nonnull UUID userId) {
        try {
            Boolean allowed = webClient.post()
                    .uri("/internal/subscription/usage/increment-question")
                    .header("X-Tenant-ID", tenantId.toString())
                    .header("X-Keycloak-User-ID", userId.toString())
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block();
            return Boolean.TRUE.equals(allowed);
        } catch (Exception e) {
            LOG.error("Failed to increment question usage for user {}: {}", userId, e.getMessage());
            return true;
        }
    }

    /**
     * Checks if a free user can start a mock exam (weekly limit).
     *
     * @param tenantId the tenant ID
     * @param userId   the user's Keycloak ID
     * @return true if allowed, false if weekly limit reached
     */
    public boolean canStartExam(@Nonnull UUID tenantId, @Nonnull UUID userId) {
        try {
            Boolean allowed = webClient.get()
                    .uri("/internal/subscription/usage/can-start-exam")
                    .header("X-Tenant-ID", tenantId.toString())
                    .header("X-Keycloak-User-ID", userId.toString())
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block();
            return Boolean.TRUE.equals(allowed);
        } catch (Exception e) {
            LOG.error("Failed to check exam limit for user {}: {}", userId, e.getMessage());
            return true;
        }
    }
}
