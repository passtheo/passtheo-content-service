package com.passtheo.content.dto.response;

import java.util.List;

/**
 * Full onboarding catalog: countries → product types → products, plus app config.
 * Returned in one API call so Flutter needs zero loading spinners during onboarding selection.
 */
public record OnboardingCatalogDto(
    List<CatalogCountryDto> countries,
    CatalogAppConfigDto appConfig,
    List<String> supportedLocales
) {

    /**
     * Country entry in the onboarding catalog.
     */
    public record CatalogCountryDto(
        String code,
        String name,
        String flagImage,
        List<String> supportedLocales,
        List<CatalogProductTypeDto> productTypes
    ) {}

    /**
     * Product type entry nested under a country.
     */
    public record CatalogProductTypeDto(
        String code,
        String name,
        String description,
        String icon,
        String regulatoryBody,
        List<CatalogProductDto> products
    ) {}

    /**
     * Product entry nested under a product type.
     */
    public record CatalogProductDto(
        String code,
        String name,
        String licenceCode,
        String description,
        String icon,
        boolean isPremium,
        CatalogExamConfigDto examConfig,
        int domainCount,
        int totalQuestions
    ) {}

    /**
     * Exam configuration nested under a product.
     */
    public record CatalogExamConfigDto(
        int totalQuestions,
        int timeLimitMinutes,
        int passScore
    ) {}

    /**
     * App-wide configuration: maintenance mode, version gate, free-tier limits.
     */
    public record CatalogAppConfigDto(
        boolean maintenanceMode,
        String minimumAppVersion,
        int freeDailyQuestionLimit,
        int freeWeeklyExamLimit,
        int freeDomainLimit,
        String supportEmail
    ) {}
}
