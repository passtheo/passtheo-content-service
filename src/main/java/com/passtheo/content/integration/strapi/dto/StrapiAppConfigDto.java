package com.passtheo.content.integration.strapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Strapi AppConfig single-type attributes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StrapiAppConfigDto(
    boolean maintenanceMode,
    String minimumAppVersion,
    int freeDailyQuestionLimit,
    int freeWeeklyExamLimit,
    int freeDomainLimit,
    String supportEmail,
    String defaultCountryCode,
    String defaultProductCode,
    String privacyPolicyUrl,
    String termsOfServiceUrl
) {}
