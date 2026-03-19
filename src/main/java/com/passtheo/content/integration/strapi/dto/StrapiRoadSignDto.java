package com.passtheo.content.integration.strapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Strapi RoadSign content type attributes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StrapiRoadSignDto(
    String name,
    String code,
    String signCategory,
    String description,
    String imageUrl,
    String shape,
    boolean isActive,
    int sortOrder
) {}
