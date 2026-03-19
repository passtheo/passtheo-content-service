package com.passtheo.content.integration.strapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Strapi Product content type attributes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StrapiProductDto(
    String name,
    String code,
    String licenceCode,
    String description,
    String icon,
    String coverImage,
    boolean isActive,
    boolean isPremium,
    int sortOrder,
    StrapiExamConfigDto examConfig,
    int domainCount,
    int totalQuestions
) {}
