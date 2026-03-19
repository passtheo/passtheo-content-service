package com.passtheo.content.dto.response;

/**
 * Product type response DTO.
 */
public record ProductTypeDto(
    String code,
    String name,
    String description,
    String icon,
    int productCount
) {}
