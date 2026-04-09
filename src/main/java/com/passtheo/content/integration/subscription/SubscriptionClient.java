package com.passtheo.content.integration.subscription;

import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;
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
     * Fetches the access grant from subscription-service's internal endpoint.
     * Called by {@link com.passtheo.content.service.EntitlementChecker} on cache miss.
     *
     * @param tenantId the tenant ID
     * @param userId   the user's Keycloak ID
     * @return the raw JSON access grant string, or null on failure
     */
    public String fetchAccessGrant(@Nonnull UUID tenantId, @Nonnull UUID userId) {
        try {
            return webClient.get()
                    .uri("/internal/subscriptions/access/{userId}", userId)
                    .header("X-Tenant-ID", tenantId.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception e) {
            LOG.error("Failed to fetch access grant for user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * Increments the daily question usage counter for a user.
     * Returns false when the 429 rate-limit response is received (daily limit reached).
     *
     * @param tenantId the tenant ID
     * @param userId   the user's Keycloak ID
     * @return true if usage increment was allowed, false if daily limit reached
     */
    public boolean incrementQuestionUsage(@Nonnull UUID tenantId, @Nonnull UUID userId) {
        try {
            webClient.post()
                    .uri("/internal/subscriptions/usage/increment")
                    .header("X-Tenant-ID", tenantId.toString())
                    .header("X-Keycloak-User-ID", userId.toString())
                    .bodyValue(Map.of("type", "QUESTION"))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            return true;
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 429) {
                return false;
            }
            LOG.error("Failed to increment question usage for user {}: {}", userId, e.getMessage());
            return true;
        } catch (Exception e) {
            LOG.error("Failed to increment question usage for user {}: {}", userId, e.getMessage());
            return true;
        }
    }

    /**
     * Checks if a user can start a mock exam (weekly limit check).
     *
     * @param tenantId the tenant ID
     * @param userId   the user's Keycloak ID
     * @return true if allowed, false if weekly limit reached
     */
    public boolean canStartExam(@Nonnull UUID tenantId, @Nonnull UUID userId) {
        try {
            Boolean allowed = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/subscriptions/usage/can-use")
                            .queryParam("type", "EXAM")
                            .build())
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
