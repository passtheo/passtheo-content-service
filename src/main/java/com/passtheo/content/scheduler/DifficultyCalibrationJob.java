package com.passtheo.content.scheduler;

import com.passtheo.content.service.QuestionDifficultyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly job — recalculates crowd-sourced difficulty scores.
 */
@Component
public class DifficultyCalibrationJob {

    private static final Logger LOG = LoggerFactory.getLogger(DifficultyCalibrationJob.class);
    private static final String DEFAULT_PRODUCT = "auto-b";

    private final QuestionDifficultyService difficultyService;

    /**
     * Constructs the difficulty calibration job.
     *
     * @param difficultyService the question difficulty service
     */
    public DifficultyCalibrationJob(QuestionDifficultyService difficultyService) {
        this.difficultyService = difficultyService;
    }

    /**
     * Runs nightly at 02:00 UTC.
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    public void calibrate() {
        LOG.info("DifficultyCalibrationJob started");
        difficultyService.calibrate(DEFAULT_PRODUCT);
        LOG.info("DifficultyCalibrationJob completed");
    }
}
