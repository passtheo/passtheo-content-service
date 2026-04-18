package com.passtheo.content.dto.response;

/**
 * Flat entry in the product catalog, returned by the internal
 * {@code GET /internal/products/catalog} endpoint. Callers such as user-service
 * use {@code code} to validate user-supplied productCode values.
 *
 * @param code             the product code (e.g. "auto-b")
 * @param name             the product's display name
 * @param productTypeCode  the parent product-type code (e.g. "cbr-auto")
 * @param countryCode      the country the product belongs to (e.g. "NL")
 * @param active           whether the product is currently available for new learners
 */
public record ProductCatalogEntryDto(
        String code,
        String name,
        String productTypeCode,
        String countryCode,
        boolean active
) {}
