package com.passtheo.content.service;

import com.passtheo.shared.core.client.UserServiceInternalClient;
import com.passtheo.content.domain.entity.ExamAttempt;
import com.passtheo.content.domain.enums.ConfidenceLabel;
import com.passtheo.content.domain.enums.DomainStrength;
import com.passtheo.content.domain.enums.ReadinessLabel;
import com.passtheo.content.domain.enums.RecommendationKey;
import com.passtheo.content.domain.valueobject.ExamConfidence;
import com.passtheo.content.domain.valueobject.ReadinessScore;
import com.passtheo.content.integration.strapi.StrapiContentCache;
import com.passtheo.content.integration.strapi.dto.StrapiDomainDto;
import com.passtheo.content.integration.strapi.dto.StrapiExamConfigDto;
import com.passtheo.content.repository.ExamAttemptRepository;
import com.passtheo.content.repository.QuestionProgressRepository;
import com.passtheo.shared.core.context.TenantContext;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
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

    // Exam confidence criteria thresholds
    private static final int CONFIDENCE_CAP = 95;
    private static final int COVERAGE_MAX_POINTS = 20;
    private static final int ACCURACY_MAX_POINTS = 25;
    private static final int EXAM_CONSISTENCY_MAX_POINTS = 30;
    private static final int AVG_SCORE_MAX_POINTS = 15;
    private static final int NO_WEAK_DOMAINS_MAX_POINTS = 10;
    private static final double COVERAGE_HIGH_THRESHOLD = 90.0;
    private static final double COVERAGE_MID_THRESHOLD = 70.0;
    private static final double ACCURACY_HIGH_THRESHOLD = 88.0;
    private static final double ACCURACY_MID_THRESHOLD = 80.0;
    private static final int CONSISTENCY_HIGH_CONSECUTIVE = 3;
    private static final double AVG_SCORE_HIGH_THRESHOLD = 46.0;
    private static final double AVG_SCORE_MID_THRESHOLD = 44.0;
    private static final int COVERAGE_MID_POINTS = 10;
    private static final int ACCURACY_MID_POINTS = 15;
    private static final int EXAM_CONSISTENCY_MID_POINTS = 15;
    private static final int AVG_SCORE_MID_POINTS = 10;

    private final QuestionProgressRepository progressRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final StrapiContentCache strapiContentCache;
    private final UserServiceInternalClient userServiceClient;

    /**
     * Constructs the readiness service.
     *
     * @param progressRepository    question progress repository
     * @param examAttemptRepository exam attempt repository
     * @param strapiContentCache    Strapi content cache
     * @param userServiceClient     user-service client for exam date
     */
    public ReadinessService(QuestionProgressRepository progressRepository,
                            ExamAttemptRepository examAttemptRepository,
                            StrapiContentCache strapiContentCache,
                            UserServiceInternalClient userServiceClient) {
        this.progressRepository = progressRepository;
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
        int questionsAttempted = progressRepository.countAttempted(userId, productCode);
        int totalCorrect = progressRepository.countCorrect(userId, productCode);
        int totalAttempts = progressRepository.countTotalAttempts(userId, productCode);
        Integer bestExamScore = examAttemptRepository.findBestScore(userId, productCode);

        StrapiExamConfigDto examConfig = strapiContentCache.getExamConfig(productCode);
        int passScore = examConfig != null ? examConfig.passScore() : 44;

        double coverage = totalQuestions > 0
                ? (double) questionsAttempted / totalQuestions * 100.0 : 0.0;
        double accuracy = totalAttempts > 0
                ? (double) totalCorrect / totalAttempts * 100.0 : 0.0;
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

        // Per-domain breakdown — computed directly from question_progress (domain_progress is never populated)
        Map<String, QuestionProgressRepository.DomainMasteryProjection> domainAggregates =
                progressRepository.aggregateByDomain(userId, productCode).stream()
                        .collect(Collectors.toMap(
                                QuestionProgressRepository.DomainMasteryProjection::getDomainCode,
                                p -> p));

        Map<String, StrapiDomainDto> strapiDomains = strapiContentCache.getDomains(productCode, locale).stream()
                .collect(Collectors.toMap(StrapiDomainDto::code, d -> d));

        List<ReadinessScore.DomainStrengthValue> domainStrengths = strapiDomains.entrySet().stream()
                .filter(e -> e.getValue().isActive())
                .map(e -> {
                    String code = e.getKey();
                    String name = e.getValue().name();
                    QuestionProgressRepository.DomainMasteryProjection agg = domainAggregates.get(code);
                    if (agg == null) {
                        return new ReadinessScore.DomainStrengthValue(
                                code, name, 0.0, 0.0, DomainStrength.UNKNOWN.name());
                    }
                    int domainTotal = strapiContentCache.getQuestionCountByDomain(code, locale);
                    double domainAccuracy = agg.getTotalAttempts() > 0
                            ? (double) agg.getCorrectCount() / agg.getTotalAttempts() * 100.0 : 0.0;
                    double domainCoverage = domainTotal > 0
                            ? (double) agg.getAttemptedCount() / domainTotal * 100.0 : 0.0;
                    DomainStrength strength = classifyDomainStrength(domainAccuracy, domainCoverage);
                    return new ReadinessScore.DomainStrengthValue(
                            code, name, domainAccuracy, domainCoverage, strength.name());
                })
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

        ExamConfidence examConfidence = calculateExamConfidence(
                userId, productCode, coverage, accuracy, passScore, domainStrengths);

        return new ReadinessScore(
                readiness, coverage, accuracy, exam, label,
                questionsAttempted, totalQuestions, bestExamScore, passScore,
                domainStrengths, examCountdownDays, predictedReadyDate, examConfidence
        );
    }

    /**
     * Calculates the composite exam confidence score (0-95) from 5 weighted criteria.
     *
     * <ul>
     *   <li>Coverage (max 20): &ge;90% &rarr; 20, 70-89% &rarr; 10, &lt;70% &rarr; 0</li>
     *   <li>Accuracy (max 25): &ge;88% &rarr; 25, 80-87% &rarr; 15, &lt;80% &rarr; 0</li>
     *   <li>Mock consistency (max 30): 3+ consecutive passes &rarr; 30, 1-2 &rarr; 15, 0 &rarr; 0</li>
     *   <li>Average score (max 15): avg &ge;46 &rarr; 15, avg &ge;44 &rarr; 10, &lt;44 &rarr; 0</li>
     *   <li>No weak domains (max 10): 0 WEAK &rarr; 10, any WEAK &rarr; 0</li>
     * </ul>
     *
     * @param userId          the user's Keycloak ID
     * @param productCode     the product code
     * @param coverage        coverage percentage (0-100)
     * @param accuracy        accuracy percentage (0-100)
     * @param passScore       the pass score threshold
     * @param domainStrengths per-domain strength breakdown
     * @return the exam confidence assessment
     */
    private ExamConfidence calculateExamConfidence(@Nonnull UUID userId, @Nonnull String productCode,
                                                   double coverage, double accuracy, int passScore,
                                                   @Nonnull List<ReadinessScore.DomainStrengthValue> domainStrengths) {
        // Criterion 1: Coverage (max 20)
        int coveragePoints;
        boolean coverageMet;
        if (coverage >= COVERAGE_HIGH_THRESHOLD) {
            coveragePoints = COVERAGE_MAX_POINTS;
            coverageMet = true;
        } else if (coverage >= COVERAGE_MID_THRESHOLD) {
            coveragePoints = COVERAGE_MID_POINTS;
            coverageMet = true;
        } else {
            coveragePoints = 0;
            coverageMet = false;
        }

        // Criterion 2: Accuracy (max 25)
        int accuracyPoints;
        boolean accuracyMet;
        if (accuracy >= ACCURACY_HIGH_THRESHOLD) {
            accuracyPoints = ACCURACY_MAX_POINTS;
            accuracyMet = true;
        } else if (accuracy >= ACCURACY_MID_THRESHOLD) {
            accuracyPoints = ACCURACY_MID_POINTS;
            accuracyMet = true;
        } else {
            accuracyPoints = 0;
            accuracyMet = false;
        }

        // Criterion 3: Mock exam consistency — consecutive passes (max 30)
        int consecutivePasses = countConsecutivePasses(userId, productCode, passScore);
        int examConsistencyPoints;
        if (consecutivePasses >= CONSISTENCY_HIGH_CONSECUTIVE) {
            examConsistencyPoints = EXAM_CONSISTENCY_MAX_POINTS;
        } else if (consecutivePasses >= 1) {
            examConsistencyPoints = EXAM_CONSISTENCY_MID_POINTS;
        } else {
            examConsistencyPoints = 0;
        }

        // Criterion 4: Average exam score (max 15)
        Double avgScore = examAttemptRepository.findAverageScore(userId, productCode);
        int avgScorePoints;
        if (avgScore != null && avgScore >= AVG_SCORE_HIGH_THRESHOLD) {
            avgScorePoints = AVG_SCORE_MAX_POINTS;
        } else if (avgScore != null && avgScore >= AVG_SCORE_MID_THRESHOLD) {
            avgScorePoints = AVG_SCORE_MID_POINTS;
        } else {
            avgScorePoints = 0;
        }

        // Criterion 5: No weak domains (max 10)
        List<String> weakDomainCodes = domainStrengths.stream()
                .filter(ds -> DomainStrength.WEAK.name().equals(ds.strength()))
                .map(ReadinessScore.DomainStrengthValue::domainCode)
                .toList();
        int noWeakDomainsPoints = weakDomainCodes.isEmpty() ? NO_WEAK_DOMAINS_MAX_POINTS : 0;

        int rawTotal = coveragePoints + accuracyPoints + examConsistencyPoints
                + avgScorePoints + noWeakDomainsPoints;
        int score = Math.min(rawTotal, CONFIDENCE_CAP);

        ConfidenceLabel label = classifyConfidenceLabel(score);
        RecommendationKey recommendation = generateRecommendationKey(score);

        ExamConfidence.Breakdown breakdown = new ExamConfidence.Breakdown(
                coveragePoints, accuracyPoints, examConsistencyPoints,
                avgScorePoints, noWeakDomainsPoints,
                coverageMet, accuracyMet, consecutivePasses, weakDomainCodes);

        return new ExamConfidence(score, label, recommendation, breakdown);
    }

    /**
     * Counts consecutive recent exam passes from most recent backward.
     * An exam counts as a pass when {@code isPassed()} is true AND the correctCount
     * meets the passScore threshold. The double-check guards against exams marked passed
     * with a stale or mismatched passScore (e.g. exam config changed after the attempt).
     *
     * @param userId      the user's Keycloak ID
     * @param productCode the product code
     * @param passScore   the current pass score threshold
     * @return number of consecutive passes from the most recent attempt
     */
    private int countConsecutivePasses(UUID userId, String productCode, int passScore) {
        List<ExamAttempt> recentExams = examAttemptRepository
                .findByKeycloakUserIdAndProductCodeOrderByCompletedAtDesc(
                        userId, productCode, PageRequest.of(0, 20))
                .getContent();

        int count = 0;
        for (ExamAttempt exam : recentExams) {
            if (exam.isPassed() && exam.getCorrectCount() >= passScore) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    /**
     * Classifies the confidence score into a machine-readable label.
     *
     * @param score the confidence score (0-95)
     * @return the confidence label
     */
    public static ConfidenceLabel classifyConfidenceLabel(int score) {
        if (score < 30) {
            return ConfidenceLabel.NOT_READY;
        }
        if (score < 60) {
            return ConfidenceLabel.GETTING_THERE;
        }
        if (score < 80) {
            return ConfidenceLabel.ALMOST_READY;
        }
        return ConfidenceLabel.READY;
    }

    /**
     * Returns a machine-readable recommendation key based on confidence score.
     * Flutter maps {@code name()} to localized strings via ARB files.
     *
     * @param score the confidence score (0-95)
     * @return the recommendation key
     */
    public static RecommendationKey generateRecommendationKey(int score) {
        if (score < 40) {
            return RecommendationKey.KEEP_PRACTICING;
        }
        if (score < 60) {
            return RecommendationKey.FOCUS_WEAK_DOMAINS;
        }
        if (score < 80) {
            return RecommendationKey.PASS_MORE_EXAMS;
        }
        return RecommendationKey.BOOK_YOUR_EXAM;
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
    public static DomainStrength classifyDomainStrength(double accuracy, double coverage) {
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
