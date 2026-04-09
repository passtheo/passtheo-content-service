package com.passtheo.content.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.passtheo.content.integration.strapi.StrapiContentCache;
import com.passtheo.shared.core.dto.AccessGrant;
import com.passtheo.content.integration.strapi.dto.StrapiDomainDto;
import com.passtheo.content.service.EntitlementChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntitlementCheckerTest {

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private StrapiContentCache strapiContentCache;

    private EntitlementChecker entitlementChecker;

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PAID_USER = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID FREE_USER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        entitlementChecker = new EntitlementChecker(redisTemplate, new ObjectMapper(), strapiContentCache);
    }

    @Test
    void paid_user_cache_hit_grants_exam_access() {
        when(valueOps.get("access:" + TENANT_ID + ":" + PAID_USER))
                .thenReturn("{\"hasAccess\":true,\"planCode\":\"MONTH_1\"}");

        assertTrue(entitlementChecker.canStartExam(TENANT_ID, PAID_USER));
    }

    @Test
    void free_user_cache_miss_denies_exam_access() {
        when(valueOps.get(anyString())).thenReturn(null);

        assertFalse(entitlementChecker.canStartExam(TENANT_ID, FREE_USER));
    }

    @Test
    void paid_user_can_start_session_any_domain() {
        when(valueOps.get("access:" + TENANT_ID + ":" + PAID_USER))
                .thenReturn("{\"hasAccess\":true,\"planCode\":\"MONTH_1\"}");

        assertTrue(entitlementChecker.canStartSession(TENANT_ID, PAID_USER, "verkeersborden", "nl"));
        assertTrue(entitlementChecker.canStartSession(TENANT_ID, PAID_USER, null, "nl"));
    }

    @Test
    void free_user_denied_mixed_session() {
        when(valueOps.get(anyString())).thenReturn(null);

        assertFalse(entitlementChecker.canStartSession(TENANT_ID, FREE_USER, null, "nl"));
    }

    @Test
    void free_user_allowed_free_preview_domain() {
        when(valueOps.get(anyString())).thenReturn(null);
        StrapiDomainDto freePreviewDomain = new StrapiDomainDto(
                1, null, "Verkeersborden", "verkeersborden", "verkeersborden",
                "desc", null, "#E63946", 50, true, true, 1);
        when(strapiContentCache.getDomains(anyString(), anyString()))
                .thenReturn(List.of(freePreviewDomain));

        assertTrue(entitlementChecker.canStartSession(TENANT_ID, FREE_USER, "verkeersborden", "nl"));
    }

    @Test
    void free_user_denied_non_free_preview_domain() {
        when(valueOps.get(anyString())).thenReturn(null);
        StrapiDomainDto lockedDomain = new StrapiDomainDto(
                1, null, "Voorrang", "voorrang", "voorrang",
                "desc", null, "#1D3557", 40, true, false, 2);
        when(strapiContentCache.getDomains(anyString(), anyString()))
                .thenReturn(List.of(lockedDomain));

        assertFalse(entitlementChecker.canStartSession(TENANT_ID, FREE_USER, "voorrang", "nl"));
    }

    @Test
    void free_user_denied_unknown_domain() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(strapiContentCache.getDomains(anyString(), anyString())).thenReturn(List.of());

        assertFalse(entitlementChecker.canStartSession(TENANT_ID, FREE_USER, "unknown-domain", "nl"));
    }

    @Test
    void malformed_redis_json_defaults_to_free() {
        when(valueOps.get(anyString())).thenReturn("{invalid-json}");

        assertFalse(entitlementChecker.canStartExam(TENANT_ID, PAID_USER));
    }

    @Test
    void get_access_returns_free_grant_on_cache_miss() {
        when(valueOps.get(anyString())).thenReturn(null);

        AccessGrant grant = entitlementChecker.getAccess(TENANT_ID, FREE_USER);

        assertFalse(grant.isPaid());
        org.junit.jupiter.api.Assertions.assertEquals("FREE", grant.planCode());
    }

    @Test
    void get_access_returns_paid_grant_on_cache_hit() {
        when(valueOps.get("access:" + TENANT_ID + ":" + PAID_USER))
                .thenReturn("{\"hasAccess\":true,\"planCode\":\"YEAR_1\"}");

        AccessGrant grant = entitlementChecker.getAccess(TENANT_ID, PAID_USER);

        assertTrue(grant.isPaid());
        org.junit.jupiter.api.Assertions.assertEquals("YEAR_1", grant.planCode());
    }
}
