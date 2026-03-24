package com.passtheo.content.integration.strapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO for Strapi 5 media/file fields (image, video).
 * Populated via populate[image][fields][0]=url etc.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StrapiMediaDto(
        int id,
        String url,
        String alternativeText,
        int width,
        int height) {
}
