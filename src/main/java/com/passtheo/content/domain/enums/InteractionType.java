package com.passtheo.content.domain.enums;

/**
 * The 6 question interaction types matching the CBR exam format (April 2025).
 */
public enum InteractionType {

    /** Select 1 from 2-4 options. */
    MULTIPLE_CHOICE("multiple_choice"),

    /** Binary yes or no. */
    YES_NO("yes_no"),

    /** Type a number (with tolerance). */
    FILL_IN_NUMBER("fill_in_number"),

    /** Tap region on image (hotspot). */
    TAP_ON_IMAGE("tap_on_image"),

    /** Drag checkmark to correct zone(s). */
    DRAG_CHECKMARK("drag_checkmark"),

    /** Drag numbers in correct order. */
    DRAG_NUMBERS("drag_numbers");

    private final String strapiValue;

    InteractionType(String strapiValue) {
        this.strapiValue = strapiValue;
    }

    /**
     * Returns the Strapi-compatible string value.
     *
     * @return the strapi value string
     */
    public String getStrapiValue() {
        return strapiValue;
    }

    /**
     * Converts a Strapi interaction type string to the enum.
     *
     * @param value the strapi value (e.g. "multiple_choice")
     * @return the matching InteractionType
     * @throws IllegalArgumentException if value is unknown
     */
    public static InteractionType fromStrapiValue(String value) {
        for (InteractionType type : values()) {
            if (type.strapiValue.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown interaction type: " + value);
    }
}
