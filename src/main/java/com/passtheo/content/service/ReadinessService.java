package com.passtheo.content.service;

import com.passtheo.content.client.UserServiceClient;
import com.passtheo.content.domain.entity.DomainProgress;
import com.passtheo.content.domain.enums.DomainStrength;
import com.passtheo.content.domain.enums.ReadinessLabel;
import com.passtheo.content.domain.valueobject.ReadinessScore;
import com.passtheo.content.integration.strapi.StrapiContentCache;
import com.passtheo.content.integration.strapi.dto.StrapiDomainDto;
import com.passtheo.content.integration.strapi.dto.StrapiExamConfigDto;
import com.passtheo.content.repository.DomainProgressRepository;
import com.passtheo.content.repository.ExamAttemptRepository;
import com.passtheo.content.repository.QuestionProgressRepository;
import com.passtheo.shared.core.context.TenantContext;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Calculates composite readiness score (0-100) using the formula:
 * (0.40 × coverage) + (0.35 × accuracy) + (0.25 × examScore).
 */
@Service
public class ReadinessService {

    private static final Logger LOG = LoggerFactory.getLogger(ReadinessService.class);

    private static final double COVERAGE_WEIGHT = 0.40;
    private static final double ACCURACY_WEIGHT = 0.35;
    private static final double EXAM_WEIGHT = 0.25;

    private static final double NOT_READY_THRESHOLD = 30.0;
    private static final double GETTING_THERE_THRESHOLD = 60.0;
    private static final double ALMOST_READY_THRESHOLD = 80.0;

    private static final double WEAK_ACCURACY_THRESHOLD = 50.0;
    private static final double MODERATE_ACCURACY_THRESHOLD = 70.0;
    private static final double STRONG_ACCURACY_THRESHOLD = 85.0;
    private static final double STRONG_COVERAGE_THRESHOLD = 60.0;
    private static final double MASTERED_COVERAGE_THRESHOLD = 80.0;

    private static final double READY_THRESHOLD = 80.0;
    private static final double DAILY_PROGRESS_ESTIMATE = 0.5;

    private final QuestionProgressRepository progressRepository;
    private final DomainProgressRepository domainProgressRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final StrapiContentCache strapiContentCache;
    private final UserServiceClient userServiceClient;

    /**
     * Constructs the readiness service.
     *
     * @param progressRepository       question progress repository
     * @param domainProgressRepository domain progress repository
     * @param examAttemptRepository    exam attempt repository
     * @param strapiContentCache       Strapi content cache
     * @param userServiceClient        user-service client for exam date
     */
    public ReadinessService(QuestionProgressRepository progressRepository,
                            DomainProgressRepository domainProgressRepository,
                            ExamAttemptRepository examAttemptRepository,
                            StrapiContentCache strapiContentCache,
                            UserServiceClient userServiceClient) {
        this.progressRepository = progressRepository;
        this.domainProgressRepository = domainProgressRepository;
        this.examAttemptRepository = examAttemptRepository;
        this.strapiContentCache = strapiContentCache;
        this.userServiceClient = userServiceClient;
    }

    /**
     * Calculates the composite readiness score for a user/product.
     *
     * @param userId      the user's Keycloak ID
     * @param productCode the product code
     * @param locale      content locale
     * @return the readiness score with domain breakdown
     */
    @Transactional(readOnly = true)
    public ReadinessScore calculate(@Nonnull UUID userId, @Nonnull String productCode,
                                    @Nonnull String locale) {
        int totalQuestions = strapiContentCache.getQuestionCount(productCode, locale);
        int attempted = progressRepository.countAttempted(userId, productCode);
        int correct = progressRepository.countCorrect(userId, productCode);
        Integer bestExamScore = examAttemptRepository.findBestScore(userId, productCode);

        StrapiExamConfigDto examConfig = strapiContentCache.getExamConfig(productCode);
        int passScore = examConfig != null ? examConfig.passScore() : 44;

        double coverage = totalQuestions > 0
                ? (double) attempted / totalQuestions * 100.0 : 0.0;
        double accuracy = attempted > 0
                ? (double) correct / attempted * 100.0 : 0.0;
        double exam = 0.0;
        if (bestExamScore != null && passScore > 0) {
            exam = Math.min(100.0, (double) bestExamScore / passScore * 100.0);
        }

        double readiness = (COVERAGE_WEIGHT * coverage)
                + (ACCURACY_WEIGHT * accuracy)
                + (EXAM_WEIGHT * exam);
        
        // Cap at 100 to handle floating-point precision issues
        readiness = Math.min(100.0, readiness);

        ReadinessLabel label = classifyReadiness(readiness);

        // Per-domain breakdown
        List<DomainProgress> domainProgressList = domainProgressRepository
                .findByKeycloakUserIdAndProductCode(userId, productCode);

        Map<String, String> domainNames = strapiContentCache.getDomains(productCode, locale).stream()
                .collect(Collectors.toMap(StrapiDomainDto::code, StrapiDomainDto::name));

        List<ReadinessScore.DomainStrengthValue> domainStrengths = domainProgressList.stream()
                .map(dp -> new ReadinessScore.DomainStrengthValue(
                        dp.getDomainCode(),
                        domainNames.getOrDefault(dp.getDomainCode(), dp.getDomainCode()),
                        dp.getAccuracyPercent() != null ? dp.getAccuracyPercent().doubleValue() : 0.0,
                        dp.getCoveragePercent() != null ? dp.getCoveragePercent().doubleValue() : 0.0,
                        dp.getStrength() != null ? dp.getStrength().name() : DomainStrength.UNKNOWN.name()
                ))
                .toList();

        LOG.debug("Readiness calculated: user={}, product={}, score={}, label={}, coverage={}, accuracy={}, exam={}",
                userId, productCode, readiness, label, coverage, accuracy, exam);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate examDate = userServiceClient.getProfile(userId, TenantContext.get())
                .map(p -> p.examDate()).orElse(null);

        Integer examCountdownDays = examDate != null
                ? (int) ChronoUnit.DAYS.between(today, examDate) : null;

        LocalDate predictedReadyDate = null;
        if (readiness < READY_THRESHOLD && DAILY_PROGRESS_ESTIMATE > 0) {
            double daysNeeded = (READY_THRESHOLD - readiness) / DAILY_PROGRESS_ESTIMATE;
            predictedReadyDate = today.plusDays((long) Math.ceil(daysNeeded));
        } else if (readiness >= READY_THRESHOLD) {
            predictedReadyDate = today;
        }

        return new ReadinessScore(
                readiness, coverage, accuracy, exam, label,
                attempted, totalQuestions, bestExamScore, passScore,
                domainStrengths, examCountdownDays, predictedReadyDate
        );
    }

    /**
     * Classifies a readiness score into a label.
     *
     * @param score the composite readiness score (0-100)
     * @return the label
     */
    public ReadinessLabel classifyReadiness(double score) {
        if (score < NOT_READY_THRESHOLD) {
            return ReadinessLabel.NOT_READY;
        }
        if (score < GETTING_THERE_THRESHOLD) {
            return ReadinessLabel.GETTING_THERE;
        }
        if (score < ALMOST_READY_THRESHOLD) {
            return ReadinessLabel.ALMOST_READY;
        }
        return ReadinessLabel.READY;
    }

    /**
     * Classifies domain strength based on accuracy and coverage thresholds.
     *
     * @param accuracy accuracy percentage
     * @param coverage coverage percentage
     * @return the domain strength
     */
    public DomainStrength classifyDomainStrength(double accuracy, double coverage) {
        if (accuracy < WEAK_ACCURACY_THRESHOLD) {
            return DomainStrength.WEAK;
        }
        if (accuracy < MODERATE_ACCURACY_THRESHOLD) {
            return DomainStrength.MODERATE;
        }
        if (accuracy >= STRONG_ACCURACY_THRESHOLD && coverage >= MASTERED_COVERAGE_THRESHOLD) {
            return DomainStrength.MASTERED;
        }
        if (accuracy >= MODERATE_ACCURACY_THRESHOLD && coverage >= STRONG_COVERAGE_THRESHOLD) {
            return DomainStrength.STRONG;
        }
        return DomainStrength.MODERATE;
    }
}
