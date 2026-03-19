package com.passtheo.content.service;

import com.passtheo.content.domain.entity.QuestionDifficulty;
import com.passtheo.content.repository.QuestionDifficultyRepository;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

/**
 * Manages crowd-sourced difficulty calibration.
 * difficulty_score = 100 - (correctRate * 100).
 */
@Service
public class QuestionDifficultyService {

    private static final Logger LOG = LoggerFactory.getLogger(QuestionDifficultyService.class);

    private final QuestionDifficultyRepository difficultyRepository;

    /**
     * Constructs the question difficulty service.
     *
     * @param difficultyRepository the difficulty repository
     */
    public QuestionDifficultyService(QuestionDifficultyRepository difficultyRepository) {
        this.difficultyRepository = difficultyRepository;
    }

    /**
     * Records an answer for difficulty tracking.
     *
     * @param questionId the Strapi question ID
     * @param isCorrect  whether the answer was correct
     */
    @Transactional
    public void recordAnswer(@Nonnull String questionId, boolean isCorrect) {
        QuestionDifficulty difficulty = difficultyRepository.findByStrapiQuestionId(questionId)
                .orElse(null);

        if (difficulty != null) {
            difficulty.setTimesAnswered(difficulty.getTimesAnswered() + 1);
            if (isCorrect) {
                difficulty.setTimesCorrect(difficulty.getTimesCorrect() + 1);
            }
            difficulty.setUpdatedAt(Instant.now());
            difficultyRepository.save(difficulty);
        }
    }

    /**
     * Recalibrates difficulty scores for all questions of a product.
     * Called by the nightly DifficultyCalibrationJob.
     *
     * @param productCode the product code
     */
    @Transactional
    public void calibrate(@Nonnull String productCode) {
        List<QuestionDifficulty> all = difficultyRepository.findByProductCode(productCode);
        int calibrated = 0;

        for (QuestionDifficulty qd : all) {
            if (qd.getTimesAnswered() >= 10) {
                double correctRate = (double) qd.getTimesCorrect() / qd.getTimesAnswered();
                double score = 100.0 - (correctRate * 100.0);
                qd.setDifficultyScore(BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP));
                qd.setCalibratedAt(Instant.now());
                qd.setUpdatedAt(Instant.now());
                difficultyRepository.save(qd);
                calibrated++;
            }
        }

        LOG.info("Difficulty calibration complete: product={}, calibrated={}/{}", productCode, calibrated, all.size());
    }
}
