package com.passtheo.content.integration.strapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Lightweight DTO for populated Strapi 5 relation fields.
 * Relations returned inline with populate; only id, documentId, code, and name are mapped.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StrapiRelationDto(
        int id,
        String documentId,
        String code,
        String name) {
}
