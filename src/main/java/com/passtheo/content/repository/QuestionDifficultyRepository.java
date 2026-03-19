package com.passtheo.content.repository;

import com.passtheo.content.domain.entity.QuestionDifficulty;
import jakarta.annotation.Nonnull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for QuestionDifficulty entities.
 */
@Repository
public interface QuestionDifficultyRepository extends JpaRepository<QuestionDifficulty, UUID> {

    /**
     * Finds difficulty for a specific question.
     *
     * @param strapiQuestionId the Strapi question ID
     * @return the difficulty record if found
     */
    Optional<QuestionDifficulty> findByStrapiQuestionId(@Nonnull String strapiQuestionId);

    /**
     * Finds all difficulty records for a product (for nightly calibration).
     *
     * @param productCode the product code
     * @return list of difficulty records
     */
    List<QuestionDifficulty> findByProductCode(@Nonnull String productCode);
}
