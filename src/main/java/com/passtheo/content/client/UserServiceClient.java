package com.passtheo.content.client;

import com.passtheo.shared.core.dto.InternalUserProfileDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Client for user-service internal profile endpoint.
 * Results are cached in Redis for 5 minutes.
 */
@Component
public class UserServiceClient {

    private static final Logger LOG = LoggerFactory.getLogger(UserServiceClient.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final String CACHE_PREFIX = "content:user-profile:";

    private final WebClient webClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Constructs the UserServiceClient.
     *
     * @param webClient     the pre-configured WebClient for user-service
     * @param redisTemplate the Redis template for caching
     */
    public UserServiceClient(@Qualifier("contentUserServiceWebClient") WebClient webClient,
                             StringRedisTemplate redisTemplate) {
        this.webClient = webClient;
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    /**
     * Fetches the internal user profile for the given Keycloak user ID and tenant ID.
     * Result cached in Redis for 5 minutes.
     *
     * @param keycloakUserId the Keycloak user ID
     * @param tenantId       the tenant ID
     * @return Optional containing the profile, empty if not found
     */
    public Optional<InternalUserProfileDto> getProfile(UUID keycloakUserId, UUID tenantId) {
        String cacheKey = CACHE_PREFIX + tenantId + ":" + keycloakUserId;

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                JsonNode root = objectMapper.readTree(cached);
                JsonNode data = root.path("data");
                InternalUserProfileDto dto = objectMapper.treeToValue(data, InternalUserProfileDto.class);
                return Optional.ofNullable(dto);
            } catch (Exception e) {
                LOG.warn("Failed to deserialize cached user profile, fetching fresh: {}", e.getMessage());
            }
        }

        try {
            String response = webClient.get()
                    .uri("/internal/users/{keycloakUserId}/profile", keycloakUserId)
                    .header("X-Tenant-ID", tenantId.toString())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response != null) {
                redisTemplate.opsForValue().set(cacheKey, response, CACHE_TTL);
                JsonNode root = objectMapper.readTree(response);
                JsonNode data = root.path("data");
                InternalUserProfileDto dto = objectMapper.treeToValue(data, InternalUserProfileDto.class);
                return Optional.ofNullable(dto);
            }
        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                LOG.warn("User profile not found for keycloakUserId={}", keycloakUserId);
                return Optional.empty();
            }
            LOG.error("Error fetching user profile for keycloakUserId={}: {}", keycloakUserId, e.getMessage());
        } catch (Exception e) {
            LOG.error("Error fetching user profile for keycloakUserId={}: {}", keycloakUserId, e.getMessage());
        }

        return Optional.empty();
    }
}
