package com.passtheo.content.integration.strapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Strapi Country content type attributes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StrapiCountryDto(
    String name,
    String code,
    String flagImage,
    String defaultLocale,
    List<String> supportedLocales,
    boolean isActive,
    int sortOrder
) {}
