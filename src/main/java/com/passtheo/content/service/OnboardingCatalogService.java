package com.passtheo.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.passtheo.content.dto.response.OnboardingCatalogDto;
import com.passtheo.content.dto.response.OnboardingCatalogDto.CatalogAppConfigDto;
import com.passtheo.content.dto.response.OnboardingCatalogDto.CatalogCountryDto;
import com.passtheo.content.dto.response.OnboardingCatalogDto.CatalogExamConfigDto;
import com.passtheo.content.dto.response.OnboardingCatalogDto.CatalogProductDto;
import com.passtheo.content.dto.response.OnboardingCatalogDto.CatalogProductTypeDto;
import com.passtheo.content.integration.strapi.StrapiContentCache;
import com.passtheo.content.integration.strapi.dto.StrapiAppConfigDto;
import com.passtheo.content.integration.strapi.dto.StrapiCountryDto;
import com.passtheo.content.integration.strapi.dto.StrapiDomainDto;
import com.passtheo.content.integration.strapi.dto.StrapiExamConfigDto;
import com.passtheo.content.integration.strapi.dto.StrapiProductDto;
import com.passtheo.content.integration.strapi.dto.StrapiProductTypeDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Assembles the full onboarding catalog (countries → product types → products + app config)
 * from the per-entity Strapi cache and stores the assembled result under its own Redis key.
 */
@Service
public class OnboardingCatalogService {

    private static final Logger LOG = LoggerFactory.getLogger(OnboardingCatalogService.class);
    static final String CACHE_KEY = "strapi:onboarding-catalog";
    private static final String DEFAULT_LOCALE = "nl";

    private final StrapiContentCache strapiContentCache;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;

    /**
     * Constructs the onboarding catalog service.
     *
     * @param strapiContentCache Strapi content cache
     * @param redisTemplate      Redis template
     * @param objectMapper       JSON object mapper
     * @param ttlSeconds         assembled-catalog cache TTL in seconds
     */
    public OnboardingCatalogService(StrapiContentCache strapiContentCache,
                                    RedisTemplate<String, String> redisTemplate,
                                    ObjectMapper objectMapper,
                                    @Value("${passtheo.content-cache.ttl-seconds:3600}") long ttlSeconds) {
        this.strapiContentCache = strapiContentCache;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheTtl = Duration.ofSeconds(ttlSeconds);
    }

    /**
     * Returns the assembled onboarding catalog, reading from the assembled cache first.
     * On a cache miss, builds from individual per-entity caches and stores the result.
     *
     * @return the full onboarding catalog
     */
    public OnboardingCatalogDto getOnboardingCatalog() {
        String cached = redisTemplate.opsForValue().get(CACHE_KEY);
        if (cached != null) {
            LOG.debug("Cache HIT: key={}", CACHE_KEY);
            try {
                return objectMapper.readValue(cached, new TypeReference<>() { });
            } catch (JsonProcessingException e) {
                LOG.warn("Cache HIT but deserialize failed for {}: {}", CACHE_KEY, e.getMessage());
            }
        }

        LOG.debug("Cache MISS: key={} — assembling from per-entity cache", CACHE_KEY);
        OnboardingCatalogDto catalog = assembleCatalog();
        storeCatalog(catalog);
        return catalog;
    }

    /**
     * Builds the nested catalog tree from the per-entity Strapi caches.
     */
    private OnboardingCatalogDto assembleCatalog() {
        List<StrapiCountryDto> countries = strapiContentCache.getCountries(DEFAULT_LOCALE);

        List<CatalogCountryDto> catalogCountries = countries.stream()
                .map(this::toCountryDto)
                .toList();

        List<String> supportedLocales = countries.stream()
                .flatMap(c -> c.supportedLocales() != null ? c.supportedLocales().stream() : java.util.stream.Stream.of())
                .distinct()
                .toList();

        CatalogAppConfigDto appConfig = toAppConfigDto(strapiContentCache.getAppConfig());

        return new OnboardingCatalogDto(catalogCountries, appConfig, supportedLocales);
    }

    private CatalogCountryDto toCountryDto(StrapiCountryDto country) {
        List<StrapiProductTypeDto> productTypes =
                strapiContentCache.getProductTypes(country.code(), DEFAULT_LOCALE);

        List<CatalogProductTypeDto> catalogProductTypes = productTypes.stream()
                .map(pt -> toProductTypeDto(pt, country.code()))
                .toList();

        return new CatalogCountryDto(
                country.code(),
                country.name(),
                country.flagImage(),
                country.supportedLocales(),
                catalogProductTypes);
    }

    private CatalogProductTypeDto toProductTypeDto(StrapiProductTypeDto productType, String countryCode) {
        List<StrapiProductDto> products =
                strapiContentCache.getProducts(productType.code(), DEFAULT_LOCALE);

        List<CatalogProductDto> catalogProducts = products.stream()
                .map(this::toProductDto)
                .toList();

        return new CatalogProductTypeDto(
                productType.code(),
                productType.name(),
                productType.description(),
                productType.icon(),
                productType.regulatoryBody(),
                catalogProducts);
    }

    private CatalogProductDto toProductDto(StrapiProductDto product) {
        StrapiExamConfigDto strapiExamConfig = strapiContentCache.getExamConfig(product.code());
        CatalogExamConfigDto examConfig = null;
        if (strapiExamConfig != null) {
            examConfig = new CatalogExamConfigDto(
                    strapiExamConfig.totalQuestions(),
                    strapiExamConfig.timeLimitMinutes(),
                    strapiExamConfig.passScore());
        }

        List<StrapiDomainDto> domains =
                strapiContentCache.getDomains(product.code(), DEFAULT_LOCALE);
        int domainCount = domains.size();
        int totalQuestions = strapiContentCache.getQuestionCount(product.code(), DEFAULT_LOCALE);

        return new CatalogProductDto(
                product.code(),
                product.name(),
                product.licenceCode(),
                product.description(),
                product.icon(),
                product.isPremium(),
                examConfig,
                domainCount,
                totalQuestions);
    }

    private CatalogAppConfigDto toAppConfigDto(StrapiAppConfigDto config) {
        if (config == null) {
            LOG.warn("AppConfig not found in Strapi — using defaults");
            return new CatalogAppConfigDto(false, "1.0.0", 10, 1, 2, "support@passtheo.nl");
        }
        return new CatalogAppConfigDto(
                config.maintenanceMode(),
                config.minimumAppVersion(),
                config.freeDailyQuestionLimit(),
                config.freeWeeklyExamLimit(),
                config.freeDomainLimit(),
                config.supportEmail());
    }

    private void storeCatalog(OnboardingCatalogDto catalog) {
        try {
            String json = objectMapper.writeValueAsString(catalog);
            redisTemplate.opsForValue().set(CACHE_KEY, json, cacheTtl);
            LOG.debug("Cache POPULATED: key={}", CACHE_KEY);
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to serialize onboarding catalog for caching: {}", e.getMessage());
        }
    }
}
