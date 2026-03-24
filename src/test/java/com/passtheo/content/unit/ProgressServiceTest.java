package com.passtheo.content.unit;

import com.passtheo.content.domain.enums.DomainStrength;
import com.passtheo.content.domain.enums.MasteryLevel;
import com.passtheo.content.dto.response.DomainProgressDto;
import com.passtheo.content.dto.response.MasteryStatsDto;
import com.passtheo.content.dto.response.ReadinessSnapshotDto;
import com.passtheo.content.integration.strapi.StrapiContentCache;
import com.passtheo.content.integration.strapi.dto.StrapiDomainDto;
import com.passtheo.content.repository.DomainProgressRepository;
import com.passtheo.content.repository.QuestionProgressRepository;
import com.passtheo.content.repository.ReadinessSnapshotRepository;
import com.passtheo.content.service.ProgressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgressServiceTest {

    @Mock private QuestionProgressRepository progressRepository;
    @Mock private DomainProgressRepository domainProgressRepository;
    @Mock private ReadinessSnapshotRepository snapshotRepository;
    @Mock private StrapiContentCache strapiContentCache;

    private ProgressService progressService;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String PRODUCT = "auto-b";
    private static final String LOCALE = "nl";

    @BeforeEach
    void setUp() {
        progressService = new ProgressService(
                progressRepository, domainProgressRepository, snapshotRepository, strapiContentCache);
    }

    @Test
    void getDomainProgress_returns_empty_for_new_user() {
        when(domainProgressRepository.findByKeycloakUserIdAndProductCode(USER_ID, PRODUCT))
                .thenReturn(List.of());
        when(strapiContentCache.getDomains(eq(PRODUCT), eq(LOCALE)))
                .thenReturn(List.of());

        List<DomainProgressDto> result = progressService.getDomainProgress(USER_ID, PRODUCT, LOCALE);

        assertTrue(result.isEmpty());
    }

    @Test
    void getDomainProgress_maps_domain_names_from_strapi() {
        com.passtheo.content.domain.entity.DomainProgress dp =
                buildDomainProgress("verkeersborden", 50, 25, 20, 5, 80.0, 50.0, DomainStrength.STRONG);
        when(domainProgressRepository.findByKeycloakUserIdAndProductCode(USER_ID, PRODUCT))
                .thenReturn(List.of(dp));
        when(strapiContentCache.getDomains(eq(PRODUCT), eq(LOCALE)))
                .thenReturn(List.of(new StrapiDomainDto(
                        1, null, "Verkeersborden", "verkeersborden", "verkeersborden",
                        "desc", null, "#E63946", 50, true, true, 1)));

        List<DomainProgressDto> result = progressService.getDomainProgress(USER_ID, PRODUCT, LOCALE);

        assertEquals(1, result.size());
        assertEquals("verkeersborden", result.get(0).domainCode());
        assertEquals("Verkeersborden", result.get(0).domainName());
        assertEquals(50, result.get(0).totalQuestions());
        assertEquals(25, result.get(0).attemptedCount());
        assertEquals(80.0, result.get(0).accuracyPercent(), 0.01);
    }

    @Test
    void getDomainProgress_uses_domain_code_as_fallback_name() {
        com.passtheo.content.domain.entity.DomainProgress dp =
                buildDomainProgress("unknown-domain", 10, 5, 4, 1, 80.0, 50.0, DomainStrength.MODERATE);
        when(domainProgressRepository.findByKeycloakUserIdAndProductCode(USER_ID, PRODUCT))
                .thenReturn(List.of(dp));
        when(strapiContentCache.getDomains(eq(PRODUCT), eq(LOCALE)))
                .thenReturn(List.of());

        List<DomainProgressDto> result = progressService.getDomainProgress(USER_ID, PRODUCT, LOCALE);

        assertEquals("unknown-domain", result.get(0).domainName());
    }

    @Test
    void getMasteryStats_calculates_percentages_correctly() {
        when(strapiContentCache.getQuestionCount(eq(PRODUCT), eq(LOCALE))).thenReturn(100);
        when(progressRepository.countByKeycloakUserIdAndProductCodeAndMasteryLevel(
                USER_ID, PRODUCT, MasteryLevel.NEW)).thenReturn(10L);
        when(progressRepository.countByKeycloakUserIdAndProductCodeAndMasteryLevel(
                USER_ID, PRODUCT, MasteryLevel.LEARNING)).thenReturn(20L);
        when(progressRepository.countByKeycloakUserIdAndProductCodeAndMasteryLevel(
                USER_ID, PRODUCT, MasteryLevel.FAMILIAR)).thenReturn(30L);
        when(progressRepository.countByKeycloakUserIdAndProductCodeAndMasteryLevel(
                USER_ID, PRODUCT, MasteryLevel.MASTERED)).thenReturn(40L);

        MasteryStatsDto result = progressService.getMasteryStats(USER_ID, PRODUCT, LOCALE);

        assertEquals(100, result.totalQuestions());
        assertEquals(40, result.mastered());
        assertEquals(30, result.familiar());
        assertEquals(20, result.learning());
        assertEquals(40.0, result.masteredPercent(), 0.01);
        assertEquals(30.0, result.familiarPercent(), 0.01);
        assertEquals(20.0, result.learningPercent(), 0.01);
    }

    @Test
    void getMasteryStats_handles_no_questions() {
        when(strapiContentCache.getQuestionCount(eq(PRODUCT), eq(LOCALE))).thenReturn(0);
        when(progressRepository.countByKeycloakUserIdAndProductCodeAndMasteryLevel(
                any(), anyString(), any())).thenReturn(0L);

        MasteryStatsDto result = progressService.getMasteryStats(USER_ID, PRODUCT, LOCALE);

        assertEquals(0, result.totalQuestions());
        assertEquals(0.0, result.masteredPercent(), 0.0);
        assertEquals(0.0, result.learningPercent(), 0.0);
    }

    @Test
    void getMasteryStats_accounts_for_unseen_questions_as_new() {
        when(strapiContentCache.getQuestionCount(eq(PRODUCT), eq(LOCALE))).thenReturn(100);
        when(progressRepository.countByKeycloakUserIdAndProductCodeAndMasteryLevel(
                USER_ID, PRODUCT, MasteryLevel.NEW)).thenReturn(0L);
        when(progressRepository.countByKeycloakUserIdAndProductCodeAndMasteryLevel(
                USER_ID, PRODUCT, MasteryLevel.LEARNING)).thenReturn(30L);
        when(progressRepository.countByKeycloakUserIdAndProductCodeAndMasteryLevel(
                USER_ID, PRODUCT, MasteryLevel.FAMILIAR)).thenReturn(20L);
        when(progressRepository.countByKeycloakUserIdAndProductCodeAndMasteryLevel(
                USER_ID, PRODUCT, MasteryLevel.MASTERED)).thenReturn(10L);

        MasteryStatsDto result = progressService.getMasteryStats(USER_ID, PRODUCT, LOCALE);

        assertEquals(40, result.newCount(), "40 unseen questions should count as NEW");
    }

    @Test
    void getReadinessTrend_returns_empty_for_no_history() {
        when(snapshotRepository.findByKeycloakUserIdAndProductCodeAndSnapshotDateAfterOrderBySnapshotDateAsc(
                eq(USER_ID), eq(PRODUCT), any(LocalDate.class))).thenReturn(List.of());

        List<ReadinessSnapshotDto> result = progressService.getReadinessTrend(USER_ID, PRODUCT, 30);

        assertTrue(result.isEmpty());
    }

    @Test
    void getReadinessTrend_maps_snapshots() {
        com.passtheo.content.domain.entity.ReadinessSnapshot snapshot =
                buildReadinessSnapshot(LocalDate.now().minusDays(1), 75.0, 60.0, 85.0);
        when(snapshotRepository.findByKeycloakUserIdAndProductCodeAndSnapshotDateAfterOrderBySnapshotDateAsc(
                eq(USER_ID), eq(PRODUCT), any(LocalDate.class))).thenReturn(List.of(snapshot));

        List<ReadinessSnapshotDto> result = progressService.getReadinessTrend(USER_ID, PRODUCT, 30);

        assertEquals(1, result.size());
        assertEquals(75.0, result.get(0).readinessScore(), 0.01);
        assertEquals(60.0, result.get(0).coverageScore(), 0.01);
        assertEquals(85.0, result.get(0).accuracyScore(), 0.01);
    }

    // ─── Helpers ───

    private com.passtheo.content.domain.entity.DomainProgress buildDomainProgress(
            String domainCode, int total, int attempted, int correct, int mastered,
            double accuracy, double coverage, DomainStrength strength) {
        com.passtheo.content.domain.entity.DomainProgress dp =
                mock(com.passtheo.content.domain.entity.DomainProgress.class);
        when(dp.getDomainCode()).thenReturn(domainCode);
        when(dp.getTotalQuestions()).thenReturn(total);
        when(dp.getAttemptedCount()).thenReturn(attempted);
        when(dp.getCorrectCount()).thenReturn(correct);
        when(dp.getMasteredCount()).thenReturn(mastered);
        when(dp.getAccuracyPercent()).thenReturn(BigDecimal.valueOf(accuracy));
        when(dp.getCoveragePercent()).thenReturn(BigDecimal.valueOf(coverage));
        when(dp.getStrength()).thenReturn(strength);
        return dp;
    }

    private com.passtheo.content.domain.entity.ReadinessSnapshot buildReadinessSnapshot(
            LocalDate date, double readiness, double coverage, double accuracy) {
        com.passtheo.content.domain.entity.ReadinessSnapshot s =
                mock(com.passtheo.content.domain.entity.ReadinessSnapshot.class);
        when(s.getSnapshotDate()).thenReturn(date);
        when(s.getReadinessScore()).thenReturn(BigDecimal.valueOf(readiness));
        when(s.getCoverageScore()).thenReturn(BigDecimal.valueOf(coverage));
        when(s.getAccuracyScore()).thenReturn(BigDecimal.valueOf(accuracy));
        return s;
    }
}
