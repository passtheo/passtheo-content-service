package com.passtheo.content.controller;

import com.passtheo.content.dto.response.ProductCatalogEntryDto;
import com.passtheo.content.integration.strapi.StrapiContentCache;
import com.passtheo.content.integration.strapi.dto.StrapiCountryDto;
import com.passtheo.content.integration.strapi.dto.StrapiProductDto;
import com.passtheo.content.integration.strapi.dto.StrapiProductTypeDto;
import com.passtheo.shared.core.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Internal service-to-service endpoint that exposes the product catalog in a
 * flat form so services that need to validate a productCode (e.g. user-service)
 * can do so without talking to Strapi directly.
 *
 * <p>Sourced from {@link StrapiContentCache}, so results honour the Redis TTL
 * and circuit-breaker protections already in place for Strapi calls.
 */
@RestController
@RequestMapping("/internal/products")
public class InternalProductController {

    private static final Logger LOG = LoggerFactory.getLogger(InternalProductController.class);

    private final StrapiContentCache strapiContentCache;

    /**
     * Constructs the internal product controller.
     *
     * @param strapiContentCache the Strapi content cache
     */
    public InternalProductController(StrapiContentCache strapiContentCache) {
        this.strapiContentCache = strapiContentCache;
    }

    /**
     * Returns every product across every country + product-type in a single flat list.
     *
     * @param locale the content locale (default "nl")
     * @return 200 OK with a list of {@link ProductCatalogEntryDto}
     */
    @GetMapping("/catalog")
    public ResponseEntity<ApiResponse<List<ProductCatalogEntryDto>>> getCatalog(
            @RequestParam(defaultValue = "nl") String locale) {
        List<ProductCatalogEntryDto> catalog = new ArrayList<>();
        List<StrapiCountryDto> countries = strapiContentCache.getCountries(locale);
        for (StrapiCountryDto country : countries) {
            List<StrapiProductTypeDto> productTypes =
                    strapiContentCache.getProductTypes(country.code(), locale);
            for (StrapiProductTypeDto productType : productTypes) {
                List<StrapiProductDto> products =
                        strapiContentCache.getProducts(productType.code(), locale);
                for (StrapiProductDto product : products) {
                    catalog.add(new ProductCatalogEntryDto(
                            product.code(),
                            product.name(),
                            productType.code(),
                            country.code(),
                            product.isActive()));
                }
            }
        }
        LOG.debug("Product catalog assembled: {} entries ({} countries)", catalog.size(), countries.size());
        return ResponseEntity.ok(ApiResponse.success(catalog, MDC.get("traceId")));
    }
}
