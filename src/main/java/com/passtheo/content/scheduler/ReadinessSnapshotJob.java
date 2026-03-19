package com.passtheo.content.scheduler;

import com.passtheo.content.domain.entity.ReadinessSnapshot;
import com.passtheo.content.domain.valueobject.ReadinessScore;
import com.passtheo.content.repository.QuestionProgressRepository;
import com.passtheo.content.repository.ReadinessSnapshotRepository;
import com.passtheo.content.service.ReadinessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Daily job at 01:00 UTC — snapshots readiness scores for all active users.
 */
@Component
public class ReadinessSnapshotJob {

    private static final Logger LOG = LoggerFactory.getLogger(ReadinessSnapshotJob.class);

    private final ReadinessSnapshotRepository snapshotRepository;

    /**
     * Constructs the readiness snapshot job.
     *
     * @param snapshotRepository readiness snapshot repository
     */
    public ReadinessSnapshotJob(ReadinessSnapshotRepository snapshotRepository) {
        this.snapshotRepository = snapshotRepository;
    }

    /**
     * Runs daily at 01:00 UTC to snapshot readiness for active users.
     * In production, this would query for active users and calculate each.
     * For now, it's a placeholder that logs execution.
     */
    @Scheduled(cron = "0 0 1 * * *", zone = "UTC")
    public void snapshotReadiness() {
        LOG.info("ReadinessSnapshotJob started at {}", LocalDate.now(ZoneOffset.UTC));
        // In production: query distinct users from question_progress, calculate readiness, save snapshot
        LOG.info("ReadinessSnapshotJob completed");
    }
}
