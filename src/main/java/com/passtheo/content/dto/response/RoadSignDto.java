package com.passtheo.content.dto.response;

/**
 * Road sign response DTO.
 */
public record RoadSignDto(
    String code,
    String name,
    String signCategory,
    String description,
    String imageUrl,
    String shape
) {}
