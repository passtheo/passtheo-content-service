package com.passtheo.content.dto.response;

import java.util.List;

/**
 * Country response DTO.
 */
public record CountryDto(
    String code,
    String name,
    String flagImage,
    List<String> supportedLocales
) {}
