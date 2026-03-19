package com.passtheo.content.integration.strapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Strapi Topic content type attributes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StrapiTopicDto(
    String name,
    String code,
    String slug,
    String description,
    String difficulty,
    Integer questionCount,
    boolean isActive,
    int sortOrder
) {}
