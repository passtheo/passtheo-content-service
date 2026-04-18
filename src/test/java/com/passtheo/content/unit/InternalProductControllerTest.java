package com.passtheo.content.unit;

import com.passtheo.content.controller.InternalProductController;
import com.passtheo.content.dto.response.ProductCatalogEntryDto;
import com.passtheo.content.integration.strapi.StrapiContentCache;
import com.passtheo.content.integration.strapi.dto.StrapiCountryDto;
import com.passtheo.content.integration.strapi.dto.StrapiProductDto;
import com.passtheo.content.integration.strapi.dto.StrapiProductTypeDto;
import com.passtheo.shared.core.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

/**
 * Unit tests for InternalProductController. Mocks StrapiContentCache to drive
 * the iteration over countries -> product types -> products.
 */
@ExtendWith(MockitoExtension.class)
class InternalProductControllerTest {

    @Mock private StrapiContentCache cache;

    private InternalProductController controller;

    @BeforeEach
    void setUp() {
        controller = new InternalProductController(cache);
    }

    @Test
    void getCatalog_returnsProductsAcrossAllCountriesAndTypes() {
        StrapiCountryDto nl = country("NL", "Netherlands");
        StrapiCountryDto de = country("DE", "Germany");
        when(cache.getCountries("nl")).thenReturn(List.of(nl, de));

        StrapiProductTypeDto cbrAuto = productType("cbr-auto", "CBR Auto");
        StrapiProductTypeDto cbrMotor = productType("cbr-motor", "CBR Motor");
        StrapiProductTypeDto deAuto = productType("de-auto", "DE Auto");
        when(cache.getProductTypes("NL", "nl")).thenReturn(List.of(cbrAuto, cbrMotor));
        when(cache.getProductTypes("DE", "nl")).thenReturn(List.of(deAuto));

        when(cache.getProducts("cbr-auto", "nl")).thenReturn(List.of(
                product("auto-b", "Auto B", true),
                product("auto-be", "Auto B+E", false)));
        when(cache.getProducts("cbr-motor", "nl")).thenReturn(List.of(
                product("motor-a", "Motor A", true)));
        when(cache.getProducts("de-auto", "nl")).thenReturn(List.of(
                product("de-b", "DE B", true)));

        ResponseEntity<ApiResponse<List<ProductCatalogEntryDto>>> response =
                controller.getCatalog("nl");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<ProductCatalogEntryDto> entries = response.getBody().getData();
        assertThat(entries).hasSize(4)
                .extracting(ProductCatalogEntryDto::code, ProductCatalogEntryDto::productTypeCode,
                        ProductCatalogEntryDto::countryCode, ProductCatalogEntryDto::active)
                .containsExactly(
                        tuple("auto-b", "cbr-auto", "NL", true),
                        tuple("auto-be", "cbr-auto", "NL", false),
                        tuple("motor-a", "cbr-motor", "NL", true),
                        tuple("de-b", "de-auto", "DE", true));
    }

    @Test
    void getCatalog_noCountries_returnsEmptyList() {
        when(cache.getCountries("nl")).thenReturn(List.of());

        ResponseEntity<ApiResponse<List<ProductCatalogEntryDto>>> response =
                controller.getCatalog("nl");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).isEmpty();
    }

    private static StrapiCountryDto country(String code, String name) {
        return new StrapiCountryDto(0, null, name, code, null, "nl", List.of("nl", "en"), true, 0);
    }

    private static StrapiProductTypeDto productType(String code, String name) {
        return new StrapiProductTypeDto(0, null, name, code, null, null, null, null, null, true, 0, 0);
    }

    private static StrapiProductDto product(String code, String name, boolean active) {
        return new StrapiProductDto(0, null, name, code, null, null, null, null, active, false, 0, null, 0, 0);
    }
}
