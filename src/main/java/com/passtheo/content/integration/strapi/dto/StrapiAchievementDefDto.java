package com.passtheo.content.integration.strapi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Strapi Achievement definition content type attributes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StrapiAchievementDefDto(
    int id,
    String documentId,
    String name,
    String code,
    String description,
    String icon,
    String lockedIcon,
    String triggerType,
    int triggerValue,
    int xpReward,
    boolean isActive,
    int sortOrder,
    String productCode
) {}
