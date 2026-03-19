package com.passtheo.content.service;

import com.passtheo.content.domain.entity.DomainProgress;
import com.passtheo.content.domain.entity.ReadinessSnapshot;
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
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Aggregates topic/domain progress and provides mastery statistics.
 */
@Service
public class ProgressService {

    private static final Logger LOG = LoggerFactory.getLogger(ProgressService.class);

    private final QuestionProgressRepository progressRepository;
    private final DomainProgressRepository domainProgressRepository;
    private final ReadinessSnapshotRepository snapshotRepository;
    private final StrapiContentCache strapiContentCache;

    /**
     * Constructs the progress service.
     *
     * @param progressRepository       question progress repository
     * @param domainProgressRepository domain progress repository
     * @param snapshotRepository       readiness snapshot repository
     * @param strapiContentCache       Strapi content cache
     */
    public ProgressService(QuestionProgressRepository progressRepository,
                           DomainProgressRepository domainProgressRepository,
                           ReadinessSnapshotRepository snapshotRepository,
                           StrapiContentCache strapiContentCache) {
        this.progressRepository = progressRepository;
        this.domainProgressRepository = domainProgressRepository;
        this.snapshotRepository = snapshotRepository;
        this.strapiContentCache = strapiContentCache;
    }

    /**
     * Gets domain progress breakdown for a user/product.
     *
     * @param userId      the user's Keycloak ID
     * @param productCode the product code
     * @param locale      content locale
     * @return list of domain progress DTOs
     */
    @Transactional(readOnly = true)
    public List<DomainProgressDto> getDomainProgress(@Nonnull UUID userId, @Nonnull String productCode,
                                                      @Nonnull String locale) {
        List<DomainProgress> progressList = domainProgressRepository
                .findByKeycloakUserIdAndProductCode(userId, productCode);

        Map<String, String> domainNames = strapiContentCache.getDomains(productCode, locale).stream()
                .collect(Collectors.toMap(StrapiDomainDto::code, StrapiDomainDto::name));

        return progressList.stream()
                .map(dp -> new DomainProgressDto(
                        dp.getDomainCode(),
                        domainNames.getOrDefault(dp.getDomainCode(), dp.getDomainCode()),
                        dp.getTotalQuestions(),
                        dp.getAttemptedCount(),
                        dp.getCorrectCount(),
                        dp.getMasteredCount(),
                        dp.getAccuracyPercent() != null ? dp.getAccuracyPercent().doubleValue() : 0.0,
                        dp.getCoveragePercent() != null ? dp.getCoveragePercent().doubleValue() : 0.0,
                        dp.getStrength() != null ? dp.getStrength().name() : DomainStrength.UNKNOWN.name()
                ))
                .toList();
    }

    /**
     * Gets mastery level distribution (pie chart data).
     *
     * @param userId      the user's Keycloak ID
     * @param productCode the product code
     * @param locale      content locale
     * @return mastery stats DTO
     */
    @Transactional(readOnly = true)
    public MasteryStatsDto getMasteryStats(@Nonnull UUID userId, @Nonnull String productCode,
                                           @Nonnull String locale) {
        int totalQuestions = strapiContentCache.getQuestionCount(productCode, locale);

        long newCount = progressRepository.countByKeycloakUserIdAndProductCodeAndMasteryLevel(
                userId, productCode, MasteryLevel.NEW);
        long learningCount = progressRepository.countByKeycloakUserIdAndProductCodeAndMasteryLevel(
                userId, productCode, MasteryLevel.LEARNING);
        long familiarCount = progressRepository.countByKeycloakUserIdAndProductCodeAndMasteryLevel(
                userId, productCode, MasteryLevel.FAMILIAR);
        long masteredCount = progressRepository.countByKeycloakUserIdAndProductCodeAndMasteryLevel(
                userId, productCode, MasteryLevel.MASTERED);

        // Questions not yet seen are also NEW
        long seenNew = newCount;
        long unseenCount = totalQuestions - (seenNew + learningCount + familiarCount + masteredCount);
        long totalNew = seenNew + Math.max(0, unseenCount);

        double newPct = totalQuestions > 0 ? (double) totalNew / totalQuestions * 100.0 : 0.0;
        double learningPct = totalQuestions > 0 ? (double) learningCount / totalQuestions * 100.0 : 0.0;
        double familiarPct = totalQuestions > 0 ? (double) familiarCount / totalQuestions * 100.0 : 0.0;
        double masteredPct = totalQuestions > 0 ? (double) masteredCount / totalQuestions * 100.0 : 0.0;

        return new MasteryStatsDto(
                totalQuestions,
                (int) totalNew, (int) learningCount, (int) familiarCount, (int) masteredCount,
                newPct, learningPct, familiarPct, masteredPct
        );
    }

    /**
     * Gets readiness trend (daily snapshots) for charting.
     *
     * @param userId      the user's Keycloak ID
     * @param productCode the product code
     * @param days        number of days to look back
     * @return list of daily readiness snapshots
     */
    @Transactional(readOnly = true)
    public List<ReadinessSnapshotDto> getReadinessTrend(@Nonnull UUID userId, @Nonnull String productCode,
                                                        int days) {
        LocalDate since = LocalDate.now().minusDays(days);
        List<ReadinessSnapshot> snapshots = snapshotRepository
                .findByKeycloakUserIdAndProductCodeAndSnapshotDateAfterOrderBySnapshotDateAsc(
                        userId, productCode, since);

        return snapshots.stream()
                .map(s -> new ReadinessSnapshotDto(
                        s.getSnapshotDate(),
                        s.getReadinessScore() != null ? s.getReadinessScore().doubleValue() : 0.0,
                        s.getCoverageScore() != null ? s.getCoverageScore().doubleValue() : 0.0,
                        s.getAccuracyScore() != null ? s.getAccuracyScore().doubleValue() : 0.0
                ))
                .toList();
    }
}
