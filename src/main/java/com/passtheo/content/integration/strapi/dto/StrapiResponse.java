package com.passtheo.content.integration.strapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Strapi 5 REST API response wrapper for collection endpoints.
 * Strapi 5 returns flat objects in data (no nested id/attributes like Strapi 4).
 *
 * @param <T> the data element type
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StrapiResponse<T>(
        List<T> data,
        StrapiMeta meta) {

    /**
     * Strapi pagination metadata.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StrapiMeta(
            StrapiPagination pagination) {
    }

    /**
     * Strapi pagination info.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StrapiPagination(
            int page,
            int pageSize,
            int pageCount,
            int total) {
    }
}
