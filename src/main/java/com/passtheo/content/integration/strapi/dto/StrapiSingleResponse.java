package com.passtheo.content.integration.strapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Strapi 5 REST API response wrapper for single-item endpoints (e.g. app-config).
 *
 * @param <T> the data element type
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StrapiSingleResponse<T>(
        T data,
        StrapiResponse.StrapiMeta meta) {
}
