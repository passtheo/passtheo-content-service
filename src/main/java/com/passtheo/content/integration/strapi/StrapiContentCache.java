package com.passtheo.content.integration.strapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.passtheo.content.integration.strapi.dto.StrapiAchievementDefDto;
import com.passtheo.content.integration.strapi.dto.StrapiAppConfigDto;
import com.passtheo.content.integration.strapi.dto.StrapiCountryDto;
import com.passtheo.content.integration.strapi.dto.StrapiDomainDto;
import com.passtheo.content.integration.strapi.dto.StrapiExamConfigDto;
import com.passtheo.content.integration.strapi.dto.StrapiLessonDto;
import com.passtheo.content.integration.strapi.dto.StrapiProductDto;
import com.passtheo.content.integration.strapi.dto.StrapiProductTypeDto;
import com.passtheo.content.integration.strapi.dto.StrapiQuestionDto;
import com.passtheo.content.integration.strapi.dto.StrapiRoadSignDto;
import com.passtheo.content.integration.strapi.dto.StrapiTopicDto;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Redis-aside cache for Strapi content.
 * TTL-based invalidation (default 1 hour). Serves stale cache if Strapi is down.
 */
@Component
public class StrapiContentCache {

    private static final Logger LOG = LoggerFactory.getLogger(StrapiContentCache.class);
    private static final String CACHE_PREFIX = "strapi:";

    private final StrapiClient strapiClient;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;

    /**
     * Constructs the Strapi content cache.
     *
     * @param strapiClient  the Strapi REST client
     * @param redisTemplate the Redis template
     * @param objectMapper  the JSON object mapper
     * @param ttlSeconds    cache TTL in seconds
     */
    public StrapiContentCache(StrapiClient strapiClient,
                              RedisTemplate<String, String> redisTemplate,
                              ObjectMapper objectMapper,
                              @Value("${passtheo.content-cache.ttl-seconds:3600}") long ttlSeconds) {
        this.strapiClient = strapiClient;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheTtl = Duration.ofSeconds(ttlSeconds);
    }

    /**
     * Gets countries with caching.
     *
     * @param locale the content locale
     * @return list of countries
     */
    public List<StrapiCountryDto> getCountries(@Nonnull String locale) {
        return getCachedOrFetch("countries:" + locale,
                new TypeReference<>() { },
                () -> strapiClient.getCountries(locale));
    }

    /**
     * Gets product types with caching.
     *
     * @param countryCode the country code
     * @param locale      the content locale
     * @return list of product types
     */
    public List<StrapiProductTypeDto> getProductTypes(@Nonnull String countryCode, @Nonnull String locale) {
        return getCachedOrFetch("productTypes:" + countryCode + ":" + locale,
                new TypeReference<>() { },
                () -> strapiClient.getProductTypes(countryCode, locale));
    }

    /**
     * Gets products with caching.
     *
     * @param productTypeCode the product type code
     * @param locale          the content locale
     * @return list of products
     */
    public List<StrapiProductDto> getProducts(@Nonnull String productTypeCode, @Nonnull String locale) {
        return getCachedOrFetch("products:" + productTypeCode + ":" + locale,
                new TypeReference<>() { },
                () -> strapiClient.getProducts(productTypeCode, locale));
    }

    /**
     * Gets domains for a product with caching.
     *
     * @param productCode the product code
     * @param locale      the content locale
     * @return list of domains
     */
    public List<StrapiDomainDto> getDomains(@Nonnull String productCode, @Nonnull String locale) {
        return getCachedOrFetch("domains:" + productCode + ":" + locale,
                new TypeReference<>() { },
                () -> strapiClient.getDomains(productCode, locale));
    }

    /**
     * Gets topics for a domain with caching.
     *
     * @param domainCode the domain code
     * @param locale     the content locale
     * @return list of topics
     */
    public List<StrapiTopicDto> getTopics(@Nonnull String domainCode, @Nonnull String locale) {
        return getCachedOrFetch("topics:" + domainCode + ":" + locale,
                new TypeReference<>() { },
                () -> strapiClient.getTopics(domainCode, locale));
    }

    /**
     * Gets questions for a topic with caching.
     *
     * @param topicCode the topic code
     * @param locale    the content locale
     * @return list of questions
     */
    public List<StrapiQuestionDto> getQuestionsByTopic(@Nonnull String topicCode, @Nonnull String locale) {
        return getCachedOrFetch("questions:topic:" + topicCode + ":" + locale,
                new TypeReference<>() { },
                () -> strapiClient.getQuestionsByTopic(topicCode, locale));
    }

    /**
     * Gets questions for a domain with caching.
     *
     * @param domainCode the domain code
     * @param locale     the content locale
     * @return list of questions
     */
    public List<StrapiQuestionDto> getQuestionsByDomain(@Nonnull String domainCode, @Nonnull String locale) {
        return getCachedOrFetch("questions:domain:" + domainCode + ":" + locale,
                new TypeReference<>() { },
                () -> strapiClient.getQuestionsByDomain(domainCode, locale));
    }

    /**
     * Gets all questions for a product with caching.
     *
     * @param productCode the product code
     * @param locale      the content locale
     * @return list of questions
     */
    public List<StrapiQuestionDto> getQuestionsByProduct(@Nonnull String productCode, @Nonnull String locale) {
        return getCachedOrFetch("questions:product:" + productCode + ":" + locale,
                new TypeReference<>() { },
                () -> strapiClient.getQuestionsByProduct(productCode, locale));
    }

    /**
     * Gets question IDs for a product (used by spaced repetition for coverage tracking).
     *
     * @param productCode the product code
     * @param locale      the content locale
     * @return list of question documentIds (locale-independent)
     */
    public List<String> getQuestionIds(@Nonnull String productCode, @Nonnull String locale) {
        List<StrapiQuestionDto> questions = getQuestionsByProduct(productCode, locale);
        return questions.stream().map(StrapiQuestionDto::documentId).toList();
    }

    /**
     * Gets total question count for a product.
     *
     * @param productCode the product code
     * @param locale      the content locale
     * @return total question count
     */
    public int getQuestionCount(@Nonnull String productCode, @Nonnull String locale) {
        return getQuestionsByProduct(productCode, locale).size();
    }

    /**
     * Gets total question count for a domain (active + approved + published).
     *
     * @param domainCode the domain code
     * @param locale     the content locale
     * @return question count for the domain
     */
    public int getQuestionCountByDomain(@Nonnull String domainCode, @Nonnull String locale) {
        return getQuestionsByDomain(domainCode, locale).size();
    }

    /**
     * Gets total question count for a topic (active + approved + published).
     *
     * @param topicCode the topic code
     * @param locale    the content locale
     * @return question count for the topic
     */
    public int getQuestionCountByTopic(@Nonnull String topicCode, @Nonnull String locale) {
        return getQuestionsByTopic(topicCode, locale).size();
    }

    /**
     * Gets a single question by ID with caching.
     *
     * @param questionId the Strapi question ID
     * @param locale     the content locale
     * @return the question, or null
     */
    public StrapiQuestionDto getQuestion(@Nonnull String questionId, @Nonnull String locale) {
        String cacheKey = CACHE_PREFIX + "question:" + questionId + ":" + locale;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            LOG.debug("Cache HIT: key={}", cacheKey);
            try {
                return objectMapper.readValue(cached, StrapiQuestionDto.class);
            } catch (JsonProcessingException e) {
                LOG.warn("Cache HIT but deserialize failed: key={}, error={}", cacheKey, e.getMessage());
            }
        }

        LOG.debug("Cache MISS: key={}", cacheKey);
        StrapiQuestionDto question = strapiClient.getQuestion(questionId, locale);
        if (question != null) {
            cacheValue(cacheKey, question);
            LOG.debug("Cache POPULATED: key={}", cacheKey);
        } else {
            LOG.warn("Strapi returned null question for id={}, locale={}", questionId, locale);
        }
        return question;
    }

    /**
     * Gets the app config with caching.
     *
     * @return the app config, or null if unavailable
     */
    public StrapiAppConfigDto getAppConfig() {
        String cacheKey = CACHE_PREFIX + "appConfig";
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            LOG.debug("Cache HIT: key={}", cacheKey);
            try {
                return objectMapper.readValue(cached, StrapiAppConfigDto.class);
            } catch (JsonProcessingException e) {
                LOG.warn("Cache HIT but deserialize failed: key={}, error={}", cacheKey, e.getMessage());
            }
        }

        LOG.debug("Cache MISS: key={}", cacheKey);
        StrapiAppConfigDto config = strapiClient.getAppConfig();
        if (config != null) {
            cacheValue(cacheKey, config, cacheTtl);
            LOG.debug("Cache POPULATED: key={}", cacheKey);
        } else {
            LOG.warn("Strapi returned null app config");
        }
        return config;
    }

    /**
     * Gets exam config for a product with caching.
     *
     * @param productCode the product code
     * @return the exam config
     */
    public StrapiExamConfigDto getExamConfig(@Nonnull String productCode) {
        String cacheKey = CACHE_PREFIX + "examConfig:" + productCode;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, StrapiExamConfigDto.class);
            } catch (JsonProcessingException e) {
                LOG.warn("Failed to deserialize cached exam config: {}", e.getMessage());
            }
        }

        StrapiExamConfigDto config = strapiClient.getExamConfig(productCode);
        if (config != null) {
            cacheValue(cacheKey, config);
        }
        return config;
    }

    /**
     * Gets achievement definitions with caching, filtered by product (includes platform-wide).
     *
     * @param productCode the product code
     * @return list of achievement definitions for the product and platform-wide
     */
    public List<StrapiAchievementDefDto> getAchievements(@Nonnull String productCode) {
        return getCachedOrFetch("achievements:" + productCode,
                new TypeReference<>() { },
                () -> strapiClient.getAchievements(productCode));
    }

    /**
     * Gets road signs with caching.
     *
     * @param countryCode the country code
     * @param locale      the content locale
     * @param category    optional category filter
     * @return list of road signs
     */
    public List<StrapiRoadSignDto> getRoadSigns(@Nonnull String countryCode, @Nonnull String locale,
                                                 String category) {
        return getCachedOrFetch("roadSigns:" + countryCode + ":" + locale
                + (category != null ? ":" + category : ""),
                new TypeReference<>() { },
                () -> strapiClient.getRoadSigns(countryCode, locale, category));
    }

    /**
     * Gets lessons with caching.
     *
     * @param topicCode the topic code
     * @param locale    the content locale
     * @return list of lessons
     */
    public List<StrapiLessonDto> getLessons(@Nonnull String topicCode, @Nonnull String locale) {
        return getCachedOrFetch("lessons:" + topicCode + ":" + locale,
                new TypeReference<>() { },
                () -> strapiClient.getLessons(topicCode, locale));
    }

    /**
     * Flushes all Strapi content cache entries.
     */
    public void flushAll() {
        LOG.info("Flushing all Strapi content cache");
        var keys = redisTemplate.keys(CACHE_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            LOG.info("Deleted {} cache entries", keys.size());
        }
    }

    /**
     * Generic cache-aside pattern: check Redis, fetch on miss, cache result.
     * On Strapi failure, serves stale cache if available.
     */
    private <T> T getCachedOrFetch(String keySuffix, TypeReference<T> typeRef, java.util.function.Supplier<T> fetcher) {
        String cacheKey = CACHE_PREFIX + keySuffix;
        String cached = redisTemplate.opsForValue().get(cacheKey);

        if (cached != null) {
            LOG.debug("Cache HIT: key={}", cacheKey);
            try {
                return objectMapper.readValue(cached, typeRef);
            } catch (JsonProcessingException e) {
                LOG.warn("Cache HIT but deserialize failed: key={}, error={}", cacheKey, e.getMessage());
            }
        } else {
            LOG.debug("Cache MISS: key={}", cacheKey);
        }

        try {
            T result = fetcher.get();
            if (result instanceof List<?> list && list.isEmpty()) {
                LOG.warn("Strapi returned empty list for key={} — skipping cache", cacheKey);
            } else if (result != null) {
                cacheValue(cacheKey, result, cacheTtl);
                LOG.debug("Cache POPULATED: key={}", cacheKey);
            } else {
                LOG.warn("Strapi returned null for key={}", cacheKey);
            }
            return result;
        } catch (Exception e) {
            LOG.error("Strapi fetch FAILED: key={}, error={} — serving stale cache if available",
                    cacheKey, e.getMessage(), e);
            if (cached != null) {
                LOG.warn("Serving STALE cache: key={}", cacheKey);
                try {
                    return objectMapper.readValue(cached, typeRef);
                } catch (JsonProcessingException ex) {
                    LOG.error("Failed to deserialize stale cache: key={}, error={}", cacheKey, ex.getMessage());
                }
            }
            return null;
        }
    }

    /**
     * Caches a value in Redis with the default TTL.
     */
    private void cacheValue(String key, Object value) {
        cacheValue(key, value, cacheTtl);
    }

    /**
     * Caches a value in Redis with an explicit TTL.
     */
    private void cacheValue(String key, Object value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to serialize cache value for key {}: {}", key, e.getMessage());
        }
    }
}
