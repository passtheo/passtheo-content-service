package com.passtheo.content.integration.strapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Generic Strapi REST API response wrapper.
 *
 * @param <T> the data element type
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StrapiResponse<T>(
    List<StrapiDataWrapper<T>> data,
    StrapiMeta meta
) {

    /**
     * Strapi data wrapper with id and attributes.
     *
     * @param <T> the attributes type
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StrapiDataWrapper<T>(
        String id,
        T attributes
    ) {}

    /**
     * Strapi pagination metadata.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StrapiMeta(
        StrapiPagination pagination
    ) {}

    /**
     * Strapi pagination info.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StrapiPagination(
        int page,
        int pageSize,
        int pageCount,
        int total
    ) {}
}
