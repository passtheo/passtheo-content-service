package com.passtheo.content.service;

import com.passtheo.content.domain.entity.QuestionProgress;
import com.passtheo.content.domain.enums.MasteryLevel;
import com.passtheo.content.integration.strapi.dto.StrapiQuestionDto;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Grades answers for all 6 interaction types and updates spaced repetition state.
 * Binary grading (correct/incorrect), not 0-5 scale. Max interval: 14 days.
 */
@Service
public class AnswerProcessingService {

    private static final Logger LOG = LoggerFactory.getLogger(AnswerProcessingService.class);

    private static final int MAX_INTERVAL_DAYS = 14;
    private static final BigDecimal MIN_EASE_FACTOR = new BigDecimal("1.30");
    private static final BigDecimal EASE_INCREMENT = new BigDecimal("0.10");
    private static final BigDecimal EASE_DECREMENT = new BigDecimal("0.20");
    private static final int FAMILIAR_THRESHOLD = 2;
    private static final int MASTERED_THRESHOLD = 4;

    /**
     * Grades an answer based on the question's interaction type.
     *
     * @param question the Strapi question data
     * @param answer   the user's answer map
     * @return true if the answer is correct
     */
    public boolean gradeAnswer(@Nonnull StrapiQuestionDto question, @Nonnull Map<String, Object> answer) {
        return switch (question.interactionType()) {
            case "multiple_choice" -> gradeMultipleChoice(question, answer);
            case "yes_no" -> gradeYesNo(question, answer);
            case "fill_in_number" -> gradeFillInNumber(question, answer);
            case "tap_on_image" -> gradeTapOnImage(question, answer);
            case "drag_checkmark" -> gradeDragCheckmark(question, answer);
            case "drag_numbers" -> gradeDragNumbers(question, answer);
            default -> {
                LOG.warn("Unknown interaction type: {}", question.interactionType());
                yield false;
            }
        };
    }

    /**
     * Updates the spaced repetition state for a question after an answer.
     * Implements modified SM-2 with 4 mastery levels and 14-day max interval.
     *
     * @param progress  the current question progress (mutated in place)
     * @param isCorrect whether the answer was correct
     * @return the previous mastery level (for reporting the change)
     */
    public MasteryLevel updateMastery(@Nonnull QuestionProgress progress, boolean isCorrect) {
        MasteryLevel previousLevel = progress.getMasteryLevel();

        progress.setTotalAttempts(progress.getTotalAttempts() + 1);
        progress.setLastAnsweredAt(Instant.now());

        if (isCorrect) {
            progress.setTotalCorrect(progress.getTotalCorrect() + 1);
            progress.setConsecutiveCorrect(progress.getConsecutiveCorrect() + 1);
            adjustEaseFactor(progress, true);
            promoteIfEligible(progress);
        } else {
            progress.setConsecutiveCorrect(0);
            adjustEaseFactor(progress, false);
            demote(progress);
        }

        calculateNextReview(progress);
        progress.setUpdatedAt(Instant.now());

        LOG.debug("Mastery updated: question={}, {}→{}, consecutive={}, interval={}d",
                progress.getStrapiQuestionId(), previousLevel, progress.getMasteryLevel(),
                progress.getConsecutiveCorrect(), progress.getIntervalDays());

        return previousLevel;
    }

    /**
     * Builds the correct answer map for a question (for returning to client).
     *
     * @param question the Strapi question data
     * @return the correct answer as a map
     */
    public Map<String, Object> buildCorrectAnswer(@Nonnull StrapiQuestionDto question) {
        return switch (question.interactionType()) {
            case "multiple_choice" -> {
                String correctId = question.answerOptions() != null
                        ? question.answerOptions().stream()
                        .filter(StrapiQuestionDto.AnswerOptionDto::isCorrect)
                        .map(StrapiQuestionDto.AnswerOptionDto::id)
                        .findFirst().orElse(null)
                        : null;
                yield Map.of("selectedOptionId", Objects.requireNonNullElse(correctId, ""));
            }
            case "yes_no" -> {
                boolean correctAnswer = Boolean.TRUE.equals(question.correctBoolean());
                yield Map.of("answer", correctAnswer);
            }
            case "fill_in_number" -> Map.of("number",
                    Objects.requireNonNullElse(question.correctNumber(), 0));
            case "tap_on_image" -> {
                String correctRegionId = question.imageRegions() != null
                        ? question.imageRegions().stream()
                        .filter(StrapiQuestionDto.ImageRegionDto::isCorrect)
                        .map(StrapiQuestionDto.ImageRegionDto::id)
                        .findFirst().orElse("")
                        : "";
                yield Map.of("tappedRegionId", correctRegionId);
            }
            case "drag_checkmark" -> {
                List<String> correctIds = question.dragTargets() != null
                        ? question.dragTargets().stream()
                        .filter(StrapiQuestionDto.DragTargetDto::isCorrect)
                        .map(StrapiQuestionDto.DragTargetDto::id)
                        .toList()
                        : List.of();
                yield Map.of("selectedTargetIds", correctIds);
            }
            case "drag_numbers" -> {
                Map<String, String> placements = new java.util.LinkedHashMap<>();
                if (question.dragTargets() != null) {
                    question.dragTargets().stream()
                            .filter(dt -> dt.correctValue() != null)
                            .forEach(dt -> placements.put(dt.id(), dt.correctValue()));
                }
                yield Map.of("placements", (Object) placements);
            }
            default -> Map.of();
        };
    }

    private boolean gradeMultipleChoice(StrapiQuestionDto question, Map<String, Object> answer) {
        String selectedOptionId = (String) answer.get("selectedOptionId");
        if (selectedOptionId == null || question.answerOptions() == null) {
            return false;
        }
        return question.answerOptions().stream()
                .filter(o -> o.id().equals(selectedOptionId))
                .anyMatch(StrapiQuestionDto.AnswerOptionDto::isCorrect);
    }

    private boolean gradeYesNo(StrapiQuestionDto question, Map<String, Object> answer) {
        Object userAnswer = answer.get("answer");
        if (userAnswer == null) {
            return false;
        }
        if (question.correctBoolean() == null) {
            LOG.error("yes_no question {} has null correctBoolean — rejecting as invalid", question.id());
            return false;
        }
        boolean userSaidYes = Boolean.TRUE.equals(userAnswer);
        return userSaidYes == question.correctBoolean();
    }

    private boolean gradeFillInNumber(StrapiQuestionDto question, Map<String, Object> answer) {
        Object numberObj = answer.get("number");
        if (numberObj == null || question.correctNumber() == null) {
            return false;
        }
        int userNumber = numberObj instanceof Number n ? n.intValue() : Integer.parseInt(numberObj.toString());
        int tolerance = question.correctNumberTolerance() != null ? question.correctNumberTolerance() : 0;
        return Math.abs(userNumber - question.correctNumber()) <= tolerance;
    }

    private boolean gradeTapOnImage(StrapiQuestionDto question, Map<String, Object> answer) {
        String tappedRegionId = (String) answer.get("tappedRegionId");
        if (tappedRegionId == null || question.imageRegions() == null) {
            return false;
        }
        return question.imageRegions().stream()
                .filter(r -> r.id().equals(tappedRegionId))
                .anyMatch(StrapiQuestionDto.ImageRegionDto::isCorrect);
    }

    @SuppressWarnings("unchecked")
    private boolean gradeDragCheckmark(StrapiQuestionDto question, Map<String, Object> answer) {
        Object selectedObj = answer.get("selectedTargetIds");
        if (selectedObj == null || question.dragTargets() == null) {
            return false;
        }
        List<String> selectedIds = (List<String>) selectedObj;
        List<String> correctIds = question.dragTargets().stream()
                .filter(StrapiQuestionDto.DragTargetDto::isCorrect)
                .map(StrapiQuestionDto.DragTargetDto::id)
                .toList();
        return selectedIds.size() == correctIds.size()
                && selectedIds.containsAll(correctIds);
    }

    @SuppressWarnings("unchecked")
    private boolean gradeDragNumbers(StrapiQuestionDto question, Map<String, Object> answer) {
        Object placementsObj = answer.get("placements");
        if (placementsObj == null || question.dragTargets() == null) {
            return false;
        }
        Map<String, String> placements = (Map<String, String>) placementsObj;
        for (StrapiQuestionDto.DragTargetDto target : question.dragTargets()) {
            if (target.correctValue() == null) {
                continue;
            }
            String placed = placements.get(target.id());
            if (!target.correctValue().equals(placed)) {
                return false;
            }
        }
        return true;
    }

    private void adjustEaseFactor(QuestionProgress progress, boolean isCorrect) {
        BigDecimal current = progress.getEaseFactor();
        BigDecimal adjusted = isCorrect
                ? current.add(EASE_INCREMENT)
                : current.subtract(EASE_DECREMENT);
        progress.setEaseFactor(adjusted.max(MIN_EASE_FACTOR));
    }

    private void promoteIfEligible(QuestionProgress progress) {
        int consecutive = progress.getConsecutiveCorrect();
        MasteryLevel current = progress.getMasteryLevel();

        switch (current) {
            case NEW -> progress.setMasteryLevel(MasteryLevel.LEARNING);
            case LEARNING -> {
                if (consecutive >= FAMILIAR_THRESHOLD) {
                    progress.setMasteryLevel(MasteryLevel.FAMILIAR);
                }
            }
            case FAMILIAR -> {
                if (consecutive >= MASTERED_THRESHOLD) {
                    progress.setMasteryLevel(MasteryLevel.MASTERED);
                }
            }
            case MASTERED -> { /* stay MASTERED */ }
        }
    }

    private void demote(QuestionProgress progress) {
        switch (progress.getMasteryLevel()) {
            case MASTERED -> progress.setMasteryLevel(MasteryLevel.FAMILIAR);
            case FAMILIAR -> progress.setMasteryLevel(MasteryLevel.LEARNING);
            case LEARNING, NEW -> progress.setMasteryLevel(MasteryLevel.LEARNING);
        }
    }

    private void calculateNextReview(QuestionProgress progress) {
        int intervalDays = switch (progress.getMasteryLevel()) {
            case NEW -> 0;
            case LEARNING -> 1;
            case FAMILIAR -> 3;
            case MASTERED -> {
                int calculated = (int) Math.ceil(
                        progress.getIntervalDays() * progress.getEaseFactor().doubleValue());
                yield Math.min(calculated, MAX_INTERVAL_DAYS);
            }
        };

        // Ensure at least the base interval for each level
        if (progress.getMasteryLevel() == MasteryLevel.MASTERED && intervalDays < 7) {
            intervalDays = 7;
        }

        progress.setIntervalDays(intervalDays);
        if (intervalDays > 0) {
            progress.setNextReviewAt(Instant.now().plus(intervalDays, ChronoUnit.DAYS));
        } else {
            progress.setNextReviewAt(null);
        }
    }
}
