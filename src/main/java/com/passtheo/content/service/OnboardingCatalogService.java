package com.passtheo.content.service;

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
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Assembles the full onboarding catalog (countries → product types → products + app config)
 * from the per-entity Strapi cache. No assembled-level caching — each entity is cached
 * individually by StrapiContentCache, and the onboarding endpoint is called rarely
 * (once per app install) so assembly overhead is negligible.
 */
@Service
public class OnboardingCatalogService {

    private static final Logger LOG = LoggerFactory.getLogger(OnboardingCatalogService.class);
    private static final String DEFAULT_LOCALE = "nl";

    private final StrapiContentCache strapiContentCache;

    /**
     * Constructs the onboarding catalog service.
     *
     * @param strapiContentCache Strapi per-entity cache
     */
    public OnboardingCatalogService(StrapiContentCache strapiContentCache) {
        this.strapiContentCache = strapiContentCache;
    }

    /**
     * Returns the assembled onboarding catalog directly from per-entity caches.
     *
     * @return the full onboarding catalog
     */
    public OnboardingCatalogDto getOnboardingCatalog() {
        LOG.debug("Assembling onboarding catalog from per-entity cache");
        return assembleCatalog();
    }

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
                productType.websiteUrl(),
                catalogProducts);
    }

    private CatalogProductDto toProductDto(StrapiProductDto product) {
        StrapiExamConfigDto strapiExamConfig = strapiContentCache.getExamConfig(product.code(), DEFAULT_LOCALE);
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
}
