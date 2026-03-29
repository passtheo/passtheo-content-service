package com.passtheo.content.integration.strapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Strapi ProductType content type attributes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StrapiProductTypeDto(
    int id,
    String documentId,
    String name,
    String code,
    String description,
    String icon,
    String coverImage,
    String regulatoryBody,
    String websiteUrl,
    boolean isActive,
    int sortOrder,
    int productCount
) {}
