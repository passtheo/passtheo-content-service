package com.passtheo.content.scheduler;

import com.passtheo.content.domain.entity.ReadinessSnapshot;
import com.passtheo.content.integration.strapi.StrapiContentCache;
import com.passtheo.content.integration.strapi.dto.StrapiExamConfigDto;
import com.passtheo.content.repository.ExamAttemptRepository;
import com.passtheo.content.repository.QuestionProgressRepository;
import com.passtheo.content.repository.QuestionProgressRepository.UserProductProjection;
import com.passtheo.content.repository.ReadinessSnapshotRepository;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Daily job at 01:00 UTC — snapshots readiness scores for all active users.
 *
 * <p>For each distinct (tenant, user, product) tuple in question_progress,
 * computes coverage, accuracy, and exam scores using the same formula as
 * {@link com.passtheo.content.service.ReadinessService} and persists a
 * {@link ReadinessSnapshot} for today. Duplicate snapshots (same user/product/date)
 * are skipped.</p>
 *
 * <p>RLS is bypassed because the application connects as the table owner
 * (no FORCE ROW LEVEL SECURITY). The job sets {@code tenantId} explicitly
 * on each entity to satisfy the NOT NULL column constraint.</p>
 */
@Component
public class ReadinessSnapshotJob {

    private static final Logger LOG = LoggerFactory.getLogger(ReadinessSnapshotJob.class);

    private static final double COVERAGE_WEIGHT = 0.40;
    private static final double ACCURACY_WEIGHT = 0.35;
    private static final double EXAM_WEIGHT = 0.25;
    private static final int SCORE_SCALE = 2;
    private static final int DEFAULT_PASS_SCORE = 44;
    private static final String DEFAULT_LOCALE = "nl";

    private final QuestionProgressRepository progressRepository;
    private final ReadinessSnapshotRepository snapshotRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final StrapiContentCache strapiContentCache;

    /**
     * Constructs the readiness snapshot job.
     *
     * @param progressRepository   question progress repository
     * @param snapshotRepository   readiness snapshot repository
     * @param examAttemptRepository exam attempt repository
     * @param strapiContentCache   Strapi content cache for question counts
     */
    public ReadinessSnapshotJob(QuestionProgressRepository progressRepository,
                                ReadinessSnapshotRepository snapshotRepository,
                                ExamAttemptRepository examAttemptRepository,
                                StrapiContentCache strapiContentCache) {
        this.progressRepository = progressRepository;
        this.snapshotRepository = snapshotRepository;
        this.examAttemptRepository = examAttemptRepository;
        this.strapiContentCache = strapiContentCache;
    }

    /**
     * Runs daily at 01:00 UTC to snapshot readiness for all active users.
     */
    @Scheduled(cron = "0 0 1 * * *", zone = "UTC")
    @Transactional
    public void snapshotReadiness() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LOG.info("ReadinessSnapshotJob started for {}", today);

        List<UserProductProjection> activeUsers = progressRepository.findDistinctActiveUserProducts();
        int created = 0;
        int skipped = 0;
        int failed = 0;

        for (UserProductProjection up : activeUsers) {
            if (snapshotRepository.existsByKeycloakUserIdAndProductCodeAndSnapshotDate(
                    up.getKeycloakUserId(), up.getProductCode(), today)) {
                skipped++;
                continue;
            }

            try {
                ReadinessSnapshot snapshot = computeSnapshot(up, today);
                snapshotRepository.save(snapshot);
                created++;
            } catch (Exception e) {
                failed++;
                LOG.warn("Failed to snapshot user={} product={}: {}",
                        up.getKeycloakUserId(), up.getProductCode(), e.getMessage());
            }
        }

        LOG.info("ReadinessSnapshotJob completed — created={}, skipped={}, failed={}, total={}",
                created, skipped, failed, activeUsers.size());
    }

    /**
     * Computes a readiness snapshot for a single user/product.
     * Uses the same weighted formula as ReadinessService:
     * readiness = (0.40 × coverage) + (0.35 × accuracy) + (0.25 × examScore)
     *
     * @param up    the user-product-tenant tuple
     * @param today the snapshot date
     * @return the populated snapshot entity (not yet persisted)
     */
    @Nonnull
    public ReadinessSnapshot computeSnapshot(@Nonnull UserProductProjection up, @Nonnull LocalDate today) {
        var userId = up.getKeycloakUserId();
        var productCode = up.getProductCode();

        int attempted = progressRepository.countAttempted(userId, productCode);
        int totalCorrect = progressRepository.countCorrect(userId, productCode);
        int totalAttempts = progressRepository.countTotalAttempts(userId, productCode);
        int totalQuestions = strapiContentCache.getQuestionCount(productCode, DEFAULT_LOCALE);
        Integer bestExamScore = examAttemptRepository.findBestScore(userId, productCode);

        StrapiExamConfigDto examConfig = strapiContentCache.getExamConfig(productCode, DEFAULT_LOCALE);
        int passScore = examConfig != null ? examConfig.passScore() : DEFAULT_PASS_SCORE;

        int clampedAttempted = Math.min(attempted, totalQuestions);
        double coverage = totalQuestions > 0
                ? (double) clampedAttempted / totalQuestions * 100.0 : 0.0;
        double accuracy = totalAttempts > 0
                ? (double) totalCorrect / totalAttempts * 100.0 : 0.0;
        double exam = 0.0;
        if (bestExamScore != null && passScore > 0) {
            exam = Math.min(100.0, (double) bestExamScore / passScore * 100.0);
        }
        double readiness = Math.min(100.0,
                COVERAGE_WEIGHT * coverage + ACCURACY_WEIGHT * accuracy + EXAM_WEIGHT * exam);

        ReadinessSnapshot snapshot = new ReadinessSnapshot();
        snapshot.setTenantId(up.getTenantId());
        snapshot.setKeycloakUserId(userId);
        snapshot.setProductCode(productCode);
        snapshot.setSnapshotDate(today);
        snapshot.setReadinessScore(toBigDecimal(readiness));
        snapshot.setCoverageScore(toBigDecimal(coverage));
        snapshot.setAccuracyScore(toBigDecimal(accuracy));
        snapshot.setExamScore(toBigDecimal(exam));
        snapshot.setQuestionsAttempted(clampedAttempted);
        snapshot.setTotalQuestions(totalQuestions);
        snapshot.setBestExamScore(bestExamScore);
        return snapshot;
    }

    private static BigDecimal toBigDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(SCORE_SCALE, RoundingMode.HALF_UP);
    }
}
