package com.passtheo.content.integration.strapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Strapi Domain content type attributes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StrapiDomainDto(
    String name,
    String code,
    String slug,
    String description,
    String icon,
    String color,
    Integer questionCount,
    boolean isActive,
    boolean isFreePreview,
    int sortOrder
) {}
