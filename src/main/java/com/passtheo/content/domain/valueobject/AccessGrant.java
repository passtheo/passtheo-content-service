package com.passtheo.content.domain.valueobject;

import java.util.List;

/**
 * Represents user access grant from the shared Redis cache.
 * Written by subscription-service, read by this service.
 *
 * @param paid           whether the user has an active paid subscription
 * @param planCode       the subscription plan code (e.g. "MONTH_1", "YEAR_1")
 * @param allowedDomains allowed domain codes (null = all for paid users)
 */
public record AccessGrant(
    boolean paid,
    String planCode,
    List<String> allowedDomains
) {

    /**
     * Returns a free-tier access grant (defensive default when cache misses).
     *
     * @return a free access grant
     */
    public static AccessGrant free() {
        return new AccessGrant(false, "FREE", List.of());
    }

    /**
     * Returns whether the user has paid access.
     *
     * @return true if paid
     */
    public boolean isPaid() {
        return paid;
    }
}
