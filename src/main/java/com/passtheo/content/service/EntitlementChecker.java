package com.passtheo.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.passtheo.content.integration.strapi.StrapiContentCache;
import com.passtheo.content.integration.strapi.dto.StrapiDomainDto;
import com.passtheo.content.integration.subscription.SubscriptionClient;
import com.passtheo.shared.core.dto.AccessGrant;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Reads the shared Redis access cache to determine user entitlements.
 * The access cache key is written by subscription-service, read here and by the gateway.
 */
@Component
public class EntitlementChecker {

    private static final Logger LOG = LoggerFactory.getLogger(EntitlementChecker.class);
    private static final String ACCESS_KEY_PREFIX = "access:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final StrapiContentCache strapiContentCache;
    private final SubscriptionClient subscriptionClient;

    /**
     * Constructs the entitlement checker.
     *
     * @param redisTemplate      the access cache Redis template (DB 2)
     * @param objectMapper       the JSON object mapper
     * @param strapiContentCache the Strapi content cache
     * @param subscriptionClient the subscription-service client for cache-miss fallback
     */
    public EntitlementChecker(@Qualifier("accessCacheRedisTemplate") RedisTemplate<String, String> redisTemplate,
                              ObjectMapper objectMapper,
                              StrapiContentCache strapiContentCache,
                              SubscriptionClient subscriptionClient) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.strapiContentCache = strapiContentCache;
        this.subscriptionClient = subscriptionClient;
    }

    /**
     * Reads the access grant from shared Redis cache.
     *
     * @param tenantId the tenant ID
     * @param userId   the user's Keycloak ID
     * @return the access grant, or FREE if not cached
     */
    public AccessGrant getAccess(@Nonnull UUID tenantId, @Nonnull UUID userId) {
        String key = ACCESS_KEY_PREFIX + tenantId + ":" + userId;
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return deserialize(cached, tenantId, userId);
        }

        // Cache miss — fetch from subscription-service and cache locally
        LOG.debug("Access cache MISS: user={}, tenant={} — fetching from subscription-service", userId, tenantId);
        String json = subscriptionClient.fetchAccessGrant(tenantId, userId);
        if (json != null) {
            try {
                redisTemplate.opsForValue().set(key, json, CACHE_TTL);
            } catch (Exception e) {
                LOG.warn("Failed to cache access grant for user {}: {}", userId, e.getMessage());
            }
            return deserialize(json, tenantId, userId);
        }

        LOG.debug("Access cache MISS + subscription-service unreachable: user={}, tenant={} — defaulting to FREE", userId, tenantId);
        return AccessGrant.free();
    }

    private AccessGrant deserialize(String json, UUID tenantId, UUID userId) {
        try {
            AccessGrant grant = objectMapper.readValue(json, AccessGrant.class);
            LOG.debug("Access grant resolved: user={}, tenant={}, isPaid={}, plan={}",
                    userId, tenantId, grant.isPaid(), grant.planCode());
            return grant;
        } catch (JsonProcessingException e) {
            LOG.error("Access cache deserialize FAILED: user={}, tenant={}, error={} — defaulting to FREE",
                    userId, tenantId, e.getMessage());
            return AccessGrant.free();
        }
    }

    /**
     * Checks if a user can start a practice session for a domain.
     *
     * @param tenantId   the tenant ID
     * @param userId     the user's Keycloak ID
     * @param domainCode the domain code (nullable for mixed)
     * @param locale     the locale for Strapi lookups
     * @return true if allowed
     */
    public boolean canStartSession(@Nonnull UUID tenantId, @Nonnull UUID userId,
                                   String domainCode, @Nonnull String locale) {
        AccessGrant grant = getAccess(tenantId, userId);
        if (grant.isPaid()) {
            LOG.debug("Session access GRANTED (paid): user={}, domain={}", userId, domainCode);
            return true;
        }
        if (domainCode == null) {
            LOG.debug("Session access DENIED (free, mixed session): user={}", userId);
            return false;
        }
        boolean isFreePreview = isDomainFreePreview(domainCode, locale);
        LOG.debug("Session access {} (free-preview={}): user={}, domain={}",
                isFreePreview ? "GRANTED" : "DENIED", isFreePreview, userId, domainCode);
        return isFreePreview;
    }

    /**
     * Checks if a user can start a mock exam.
     *
     * @param tenantId the tenant ID
     * @param userId   the user's Keycloak ID
     * @return true if the user has paid access
     */
    public boolean canStartExam(@Nonnull UUID tenantId, @Nonnull UUID userId) {
        boolean isPaid = getAccess(tenantId, userId).isPaid();
        LOG.debug("Exam access {}: user={}, tenant={}", isPaid ? "GRANTED" : "DENIED", userId, tenantId);
        return isPaid;
    }

    /**
     * Checks if a domain is available for free preview.
     *
     * @param domainCode the domain code
     * @param locale     the content locale
     * @return true if the domain has isFreePreview=true in Strapi
     */
    private boolean isDomainFreePreview(@Nonnull String domainCode, @Nonnull String locale) {
        List<StrapiDomainDto> domains = strapiContentCache.getDomains("auto-b", locale);
        return domains.stream()
                .filter(d -> d.code().equals(domainCode))
                .findFirst()
                .map(StrapiDomainDto::isFreePreview)
                .orElse(false);
    }
}
