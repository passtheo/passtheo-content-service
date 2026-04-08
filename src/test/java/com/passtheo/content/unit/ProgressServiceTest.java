package com.passtheo.content.unit;

import com.passtheo.content.domain.enums.DomainStrength;
import com.passtheo.content.domain.enums.MasteryLevel;
import com.passtheo.content.dto.response.DomainProgressDto;
import com.passtheo.content.dto.response.MasteryStatsDto;
import com.passtheo.content.dto.response.ReadinessSnapshotDto;
import com.passtheo.content.integration.strapi.StrapiContentCache;
import com.passtheo.content.integration.strapi.dto.StrapiDomainDto;
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
    @Mock private ReadinessSnapshotRepository snapshotRepository;
    @Mock private StrapiContentCache strapiContentCache;

    private ProgressService progressService;

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String PRODUCT = "auto-b";
    private static final String LOCALE = "nl";

    @BeforeEach
    void setUp() {
        progressService = new ProgressService(progressRepository, snapshotRepository, strapiContentCache);
    }

    @Test
    void getDomainProgress_returns_empty_for_new_user() {
        when(progressRepository.aggregateByDomain(USER_ID, PRODUCT)).thenReturn(List.of());
        when(strapiContentCache.getDomains(eq(PRODUCT), eq(LOCALE))).thenReturn(List.of());

        List<DomainProgressDto> result = progressService.getDomainProgress(USER_ID, PRODUCT, LOCALE);

        assertTrue(result.isEmpty());
    }

    @Test
    void getDomainProgress_computes_stats_from_question_progress() {
        StrapiDomainDto domain = new StrapiDomainDto(
                1, null, "Verkeersborden", "verkeersborden", "verkeersborden",
                "desc", null, "#E63946", 30, true, true, 1);

        QuestionProgressRepository.DomainMasteryProjection agg = buildProjection(
                "verkeersborden", 25L, 22L, 30L, 5L);

        when(progressRepository.aggregateByDomain(USER_ID, PRODUCT)).thenReturn(List.of(agg));
        when(strapiContentCache.getDomains(eq(PRODUCT), eq(LOCALE))).thenReturn(List.of(domain));
        when(strapiContentCache.getQuestionCountByDomain(eq("verkeersborden"), eq(LOCALE))).thenReturn(30);

        List<DomainProgressDto> result = progressService.getDomainProgress(USER_ID, PRODUCT, LOCALE);

        assertEquals(1, result.size());
        DomainProgressDto dto = result.get(0);
        assertEquals("verkeersborden", dto.domainCode());
        assertEquals("Verkeersborden", dto.domainName());
        assertEquals(30, dto.totalQuestions());
        assertEquals(25, dto.attemptedCount());
        assertEquals(5, dto.masteredCount());
        // accuracy = 22/30*100 ≈ 73.3%
        assertEquals(73.3, dto.accuracyPercent(), 0.1);
        // coverage = 25/30*100 ≈ 83.3%
        assertEquals(83.3, dto.coveragePercent(), 0.1);
        // accuracy 73.3% >= 70%, coverage 83.3% >= 60% → STRONG
        assertEquals(DomainStrength.STRONG.name(), dto.strength());
    }

    @Test
    void getDomainProgress_returns_unknown_strength_for_domain_with_no_progress() {
        StrapiDomainDto domain = new StrapiDomainDto(
                1, null, "Verkeersborden", "verkeersborden", "verkeersborden",
                "desc", null, "#E63946", 30, true, true, 1);

        when(progressRepository.aggregateByDomain(USER_ID, PRODUCT)).thenReturn(List.of());
        when(strapiContentCache.getDomains(eq(PRODUCT), eq(LOCALE))).thenReturn(List.of(domain));
        when(strapiContentCache.getQuestionCountByDomain(eq("verkeersborden"), eq(LOCALE))).thenReturn(30);

        List<DomainProgressDto> result = progressService.getDomainProgress(USER_ID, PRODUCT, LOCALE);

        assertEquals(1, result.size());
        assertEquals(0, result.get(0).attemptedCount());
        assertEquals(DomainStrength.UNKNOWN.name(), result.get(0).strength());
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

    // ─── Deactivated question resilience ───

    @Test
    void getMasteryStats_clampsWhenProgressExceedsActivePool() {
        // Scenario: active pool is 80 questions, but user has progress on 100 (20 were deactivated)
        when(strapiContentCache.getQuestionCount(eq(PRODUCT), eq(LOCALE))).thenReturn(80);
        when(progressRepository.countByKeycloakUserIdAndProductCodeAndMasteryLevel(
                USER_ID, PRODUCT, MasteryLevel.NEW)).thenReturn(10L);
        when(progressRepository.countByKeycloakUserIdAndProductCodeAndMasteryLevel(
                USER_ID, PRODUCT, MasteryLevel.LEARNING)).thenReturn(20L);
        when(progressRepository.countByKeycloakUserIdAndProductCodeAndMasteryLevel(
                USER_ID, PRODUCT, MasteryLevel.FAMILIAR)).thenReturn(30L);
        when(progressRepository.countByKeycloakUserIdAndProductCodeAndMasteryLevel(
                USER_ID, PRODUCT, MasteryLevel.MASTERED)).thenReturn(40L);

        MasteryStatsDto result = progressService.getMasteryStats(USER_ID, PRODUCT, LOCALE);

        assertEquals(80, result.totalQuestions());
        // Sum of all levels (100) > active pool (80) → counts should be scaled down
        int total = result.newCount() + result.learning() + result.familiar() + result.mastered();
        assertTrue(total <= 80, "Total mastery counts should not exceed active pool size, got " + total);
        // Percentages should not exceed 100%
        assertTrue(result.masteredPercent() <= 100.0, "Mastered % should not exceed 100");
        assertTrue(result.learningPercent() <= 100.0, "Learning % should not exceed 100");
    }

    @Test
    void getDomainProgress_coverageClampedWhenAttemptedExceedsDomainTotal() {
        // Domain has 20 active questions, but user has attempted 30 (10 deactivated)
        StrapiDomainDto domain = new StrapiDomainDto(
                1, null, "Verkeersborden", "verkeersborden", "verkeersborden",
                "desc", null, "#E63946", 20, true, true, 1);

        QuestionProgressRepository.DomainMasteryProjection agg = buildProjection(
                "verkeersborden", 30L, 25L, 40L, 8L);

        when(progressRepository.aggregateByDomain(USER_ID, PRODUCT)).thenReturn(List.of(agg));
        when(strapiContentCache.getDomains(eq(PRODUCT), eq(LOCALE))).thenReturn(List.of(domain));
        when(strapiContentCache.getQuestionCountByDomain(eq("verkeersborden"), eq(LOCALE))).thenReturn(20);

        List<DomainProgressDto> result = progressService.getDomainProgress(USER_ID, PRODUCT, LOCALE);

        assertEquals(1, result.size());
        DomainProgressDto dto = result.get(0);
        // Coverage should be clamped: min(30, 20) / 20 = 100%, not 150%
        assertTrue(dto.coveragePercent() <= 100.0, "Coverage should not exceed 100%, got " + dto.coveragePercent());
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

    private QuestionProgressRepository.DomainMasteryProjection buildProjection(
            String domainCode, long attempted, long correct, long totalAttempts, long mastered) {
        QuestionProgressRepository.DomainMasteryProjection p =
                mock(QuestionProgressRepository.DomainMasteryProjection.class);
        when(p.getDomainCode()).thenReturn(domainCode);
        when(p.getAttemptedCount()).thenReturn(attempted);
        when(p.getCorrectCount()).thenReturn(correct);
        when(p.getTotalAttempts()).thenReturn(totalAttempts);
        when(p.getMasteredCount()).thenReturn(mastered);
        return p;
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
