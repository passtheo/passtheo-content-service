package com.passtheo.content.unit;

import com.passtheo.content.domain.entity.QuestionProgress;
import com.passtheo.content.domain.enums.MasteryLevel;
import com.passtheo.content.integration.strapi.dto.StrapiQuestionDto;
import com.passtheo.content.service.AnswerProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AnswerProcessingService — grades all 8 interaction types
 * and updates spaced repetition mastery levels.
 */
class AnswerProcessingServiceTest {

    private AnswerProcessingService service;

    @BeforeEach
    void setUp() {
        service = new AnswerProcessingService();
    }

    // ─── MULTIPLE CHOICE ───

    @Test
    void gradeAnswer_multipleChoice_correctOption_returnsTrue() {
        StrapiQuestionDto question = buildQuestion("multiple_choice",
                List.of(new StrapiQuestionDto.AnswerOptionDto(1, "Correct", null, true, 1),
                        new StrapiQuestionDto.AnswerOptionDto(2, "Wrong", null, false, 2)),
                null, null);
        assertThat(service.gradeAnswer(question, Map.of("selectedOptionId", "1"))).isTrue();
    }

    @Test
    void gradeAnswer_multipleChoice_wrongOption_returnsFalse() {
        StrapiQuestionDto question = buildQuestion("multiple_choice",
                List.of(new StrapiQuestionDto.AnswerOptionDto(1, "Correct", null, true, 1),
                        new StrapiQuestionDto.AnswerOptionDto(2, "Wrong", null, false, 2)),
                null, null);
        assertThat(service.gradeAnswer(question, Map.of("selectedOptionId", "2"))).isFalse();
    }

    // ─── YES/NO ───

    @Test
    void gradeAnswer_yesNo_correctBoolean_true_userAnswersTrue_returnsTrue() {
        StrapiQuestionDto question = buildYesNoQuestion(true);
        assertThat(service.gradeAnswer(question, Map.of("answer", true))).isTrue();
    }

    @Test
    void gradeAnswer_yesNo_correctBoolean_true_userAnswersFalse_returnsFalse() {
        StrapiQuestionDto question = buildYesNoQuestion(true);
        assertThat(service.gradeAnswer(question, Map.of("answer", false))).isFalse();
    }

    @Test
    void gradeAnswer_yesNo_correctBoolean_false_userAnswersFalse_returnsTrue() {
        StrapiQuestionDto question = buildYesNoQuestion(false);
        assertThat(service.gradeAnswer(question, Map.of("answer", false))).isTrue();
    }

    @Test
    void gradeAnswer_yesNo_correctBoolean_null_returnsFalse() {
        StrapiQuestionDto question = buildQuestion("yes_no", null, null, null);
        assertThat(service.gradeAnswer(question, Map.of("answer", true))).isFalse();
    }

    // ─── FILL IN NUMBER ───

    @Test
    void gradeAnswer_fillInNumber_exactMatch_returnsTrue() {
        StrapiQuestionDto question = buildFillInNumberQuestion(50, 0);
        assertThat(service.gradeAnswer(question, Map.of("number", 50))).isTrue();
    }

    @Test
    void gradeAnswer_fillInNumber_withinTolerance_returnsTrue() {
        StrapiQuestionDto question = buildFillInNumberQuestion(50, 5);
        assertThat(service.gradeAnswer(question, Map.of("number", 53))).isTrue();
    }

    @Test
    void gradeAnswer_fillInNumber_outsideTolerance_returnsFalse() {
        StrapiQuestionDto question = buildFillInNumberQuestion(50, 5);
        assertThat(service.gradeAnswer(question, Map.of("number", 60))).isFalse();
    }

    // ─── TAP ON IMAGE ───

    @Test
    void gradeAnswer_tapOnImage_correctRegion_returnsTrue() {
        StrapiQuestionDto question = buildQuestion("tap_on_image", null,
                List.of(new StrapiQuestionDto.ImageRegionDto(1, "Region A", 10, 20, 30, 40, true, null),
                        new StrapiQuestionDto.ImageRegionDto(2, "Region B", 50, 60, 30, 40, false, null)),
                null);
        assertThat(service.gradeAnswer(question, Map.of("tappedRegionId", "1"))).isTrue();
    }

    @Test
    void gradeAnswer_tapOnImage_wrongRegion_returnsFalse() {
        StrapiQuestionDto question = buildQuestion("tap_on_image", null,
                List.of(new StrapiQuestionDto.ImageRegionDto(1, "Region A", 10, 20, 30, 40, true, null),
                        new StrapiQuestionDto.ImageRegionDto(2, "Region B", 50, 60, 30, 40, false, null)),
                null);
        assertThat(service.gradeAnswer(question, Map.of("tappedRegionId", "2"))).isFalse();
    }

    // ─── DRAG CHECKMARK ───

    @Test
    void gradeAnswer_dragCheckmark_allCorrectTargets_returnsTrue() {
        StrapiQuestionDto question = buildQuestion("drag_checkmark", null, null,
                List.of(new StrapiQuestionDto.DragTargetDto(1, "Target 1", null, true, 1, null),
                        new StrapiQuestionDto.DragTargetDto(2, "Target 2", null, false, 2, null),
                        new StrapiQuestionDto.DragTargetDto(3, "Target 3", null, true, 3, null)));
        assertThat(service.gradeAnswer(question, Map.of("selectedTargetIds", List.of("1", "3")))).isTrue();
    }

    @Test
    void gradeAnswer_dragCheckmark_missingTarget_returnsFalse() {
        StrapiQuestionDto question = buildQuestion("drag_checkmark", null, null,
                List.of(new StrapiQuestionDto.DragTargetDto(1, "Target 1", null, true, 1, null),
                        new StrapiQuestionDto.DragTargetDto(3, "Target 3", null, true, 3, null)));
        assertThat(service.gradeAnswer(question, Map.of("selectedTargetIds", List.of("1")))).isFalse();
    }

    // ─── DRAG NUMBERS ───

    @Test
    void gradeAnswer_dragNumbers_correctOrder_returnsTrue() {
        StrapiQuestionDto question = buildQuestion("drag_numbers", null, null,
                List.of(new StrapiQuestionDto.DragTargetDto(1, "Pos 1", "1", false, 1, null),
                        new StrapiQuestionDto.DragTargetDto(2, "Pos 2", "2", false, 2, null)));
        assertThat(service.gradeAnswer(question, Map.of("placements", Map.of("1", "1", "2", "2")))).isTrue();
    }

    @Test
    void gradeAnswer_dragNumbers_wrongOrder_returnsFalse() {
        StrapiQuestionDto question = buildQuestion("drag_numbers", null, null,
                List.of(new StrapiQuestionDto.DragTargetDto(1, "Pos 1", "1", false, 1, null),
                        new StrapiQuestionDto.DragTargetDto(2, "Pos 2", "2", false, 2, null)));
        assertThat(service.gradeAnswer(question, Map.of("placements", Map.of("1", "2", "2", "1")))).isFalse();
    }

    // ─── MULTIPLE RESPONSE ───

    @Test
    void gradeAnswer_multipleResponse_allCorrectSelected_returnsTrue() {
        StrapiQuestionDto question = buildQuestion("multiple_response",
                List.of(new StrapiQuestionDto.AnswerOptionDto(1, "A", null, true, 0),
                        new StrapiQuestionDto.AnswerOptionDto(2, "B", null, false, 1),
                        new StrapiQuestionDto.AnswerOptionDto(3, "C", null, true, 2),
                        new StrapiQuestionDto.AnswerOptionDto(4, "D", null, false, 3)),
                null, null);
        assertThat(service.gradeAnswer(question, Map.of("selectedOptionIds", List.of("1", "3")))).isTrue();
    }

    @Test
    void gradeAnswer_multipleResponse_missingCorrectOption_returnsFalse() {
        StrapiQuestionDto question = buildQuestion("multiple_response",
                List.of(new StrapiQuestionDto.AnswerOptionDto(1, "A", null, true, 0),
                        new StrapiQuestionDto.AnswerOptionDto(2, "B", null, false, 1),
                        new StrapiQuestionDto.AnswerOptionDto(3, "C", null, true, 2)),
                null, null);
        assertThat(service.gradeAnswer(question, Map.of("selectedOptionIds", List.of("1")))).isFalse();
    }

    @Test
    void gradeAnswer_multipleResponse_extraIncorrectOption_returnsFalse() {
        StrapiQuestionDto question = buildQuestion("multiple_response",
                List.of(new StrapiQuestionDto.AnswerOptionDto(1, "A", null, true, 0),
                        new StrapiQuestionDto.AnswerOptionDto(2, "B", null, false, 1),
                        new StrapiQuestionDto.AnswerOptionDto(3, "C", null, true, 2)),
                null, null);
        assertThat(service.gradeAnswer(question, Map.of("selectedOptionIds", List.of("1", "2", "3")))).isFalse();
    }

    @Test
    void gradeAnswer_multipleResponse_nullAnswer_returnsFalse() {
        StrapiQuestionDto question = buildQuestion("multiple_response",
                List.of(new StrapiQuestionDto.AnswerOptionDto(1, "A", null, true, 0)),
                null, null);
        assertThat(service.gradeAnswer(question, Map.of())).isFalse();
    }

    // ─── VIDEO QUESTION ───

    @Test
    void gradeAnswer_videoQuestion_correctOption_returnsTrue() {
        StrapiQuestionDto question = buildQuestion("video_question",
                List.of(new StrapiQuestionDto.AnswerOptionDto(1, "Right", null, true, 0),
                        new StrapiQuestionDto.AnswerOptionDto(2, "Wrong", null, false, 1)),
                null, null);
        assertThat(service.gradeAnswer(question, Map.of("selectedOptionId", "1"))).isTrue();
    }

    @Test
    void gradeAnswer_videoQuestion_wrongOption_returnsFalse() {
        StrapiQuestionDto question = buildQuestion("video_question",
                List.of(new StrapiQuestionDto.AnswerOptionDto(1, "Right", null, true, 0),
                        new StrapiQuestionDto.AnswerOptionDto(2, "Wrong", null, false, 1)),
                null, null);
        assertThat(service.gradeAnswer(question, Map.of("selectedOptionId", "2"))).isFalse();
    }

    // ─── DRAG NUMBERS WITH IMAGE REGIONS ───

    @Test
    void gradeAnswer_dragNumbers_imageRegions_correctOrder_returnsTrue() {
        StrapiQuestionDto question = buildQuestion("drag_numbers", null,
                List.of(new StrapiQuestionDto.ImageRegionDto(1, "Car A", 20, 70, 14, 16, false, "3"),
                        new StrapiQuestionDto.ImageRegionDto(2, "Car B", 60, 40, 16, 14, false, "2"),
                        new StrapiQuestionDto.ImageRegionDto(3, "Car C", 30, 10, 14, 16, false, "1")),
                null);
        assertThat(service.gradeAnswer(question, Map.of("placements", Map.of("1", "3", "2", "2", "3", "1")))).isTrue();
    }

    @Test
    void gradeAnswer_dragNumbers_imageRegions_wrongOrder_returnsFalse() {
        StrapiQuestionDto question = buildQuestion("drag_numbers", null,
                List.of(new StrapiQuestionDto.ImageRegionDto(1, "Car A", 20, 70, 14, 16, false, "3"),
                        new StrapiQuestionDto.ImageRegionDto(2, "Car B", 60, 40, 16, 14, false, "2"),
                        new StrapiQuestionDto.ImageRegionDto(3, "Car C", 30, 10, 14, 16, false, "1")),
                null);
        assertThat(service.gradeAnswer(question, Map.of("placements", Map.of("1", "1", "2", "2", "3", "3")))).isFalse();
    }

    @Test
    void gradeAnswer_dragNumbers_imageRegions_missingPlacement_returnsFalse() {
        StrapiQuestionDto question = buildQuestion("drag_numbers", null,
                List.of(new StrapiQuestionDto.ImageRegionDto(1, "Car A", 20, 70, 14, 16, false, "2"),
                        new StrapiQuestionDto.ImageRegionDto(2, "Car B", 60, 40, 16, 14, false, "1")),
                null);
        assertThat(service.gradeAnswer(question, Map.of("placements", Map.of("1", "2")))).isFalse();
    }

    @Test
    void gradeAnswer_dragNumbers_prefersImageRegionsOverDragTargets() {
        // When both exist, imageRegions take priority (matches frontend DragNumbersRenderer).
        // Frontend sends imageRegion IDs, so grading must use those too.
        StrapiQuestionDto question = buildQuestion("drag_numbers", null,
                List.of(new StrapiQuestionDto.ImageRegionDto(808, "Car A", 20, 70, 14, 16, false, "1"),
                        new StrapiQuestionDto.ImageRegionDto(809, "Car B", 60, 40, 16, 14, false, "2")),
                List.of(new StrapiQuestionDto.DragTargetDto(681, "Car A", "1", false, 0, null),
                        new StrapiQuestionDto.DragTargetDto(682, "Car B", "2", false, 1, null)));
        // User sends imageRegion IDs (808, 809) — must be graded correctly
        assertThat(service.gradeAnswer(question, Map.of("placements", Map.of("808", "1", "809", "2")))).isTrue();
        // User sends dragTarget IDs (681, 682) — would fail because grading uses imageRegion IDs
        assertThat(service.gradeAnswer(question, Map.of("placements", Map.of("681", "1", "682", "2")))).isFalse();
    }

    // ─── BUILD CORRECT ANSWER ───

    @Test
    void buildCorrectAnswer_multipleResponse_returnsAllCorrectIds() {
        StrapiQuestionDto question = buildQuestion("multiple_response",
                List.of(new StrapiQuestionDto.AnswerOptionDto(1, "A", null, true, 0),
                        new StrapiQuestionDto.AnswerOptionDto(2, "B", null, false, 1),
                        new StrapiQuestionDto.AnswerOptionDto(3, "C", null, true, 2)),
                null, null);
        Map<String, Object> correct = service.buildCorrectAnswer(question);
        assertThat(correct).containsKey("selectedOptionIds");
        @SuppressWarnings("unchecked")
        List<String> ids = (List<String>) correct.get("selectedOptionIds");
        assertThat(ids).containsExactly("1", "3");
    }

    @Test
    void buildCorrectAnswer_videoQuestion_returnsSingleCorrectId() {
        StrapiQuestionDto question = buildQuestion("video_question",
                List.of(new StrapiQuestionDto.AnswerOptionDto(5, "Right", null, true, 0),
                        new StrapiQuestionDto.AnswerOptionDto(6, "Wrong", null, false, 1)),
                null, null);
        Map<String, Object> correct = service.buildCorrectAnswer(question);
        assertThat(correct.get("selectedOptionId")).isEqualTo("5");
    }

    @Test
    void buildCorrectAnswer_dragNumbers_imageRegions_returnsPlacementsFromRegions() {
        StrapiQuestionDto question = buildQuestion("drag_numbers", null,
                List.of(new StrapiQuestionDto.ImageRegionDto(1, "Car A", 20, 70, 14, 16, false, "2"),
                        new StrapiQuestionDto.ImageRegionDto(2, "Car B", 60, 40, 16, 14, false, "1")),
                null);
        Map<String, Object> correct = service.buildCorrectAnswer(question);
        @SuppressWarnings("unchecked")
        Map<String, String> placements = (Map<String, String>) correct.get("placements");
        assertThat(placements).containsEntry("1", "2").containsEntry("2", "1");
    }

    @Test
    void buildCorrectAnswer_dragNumbers_bothFieldsPresent_usesImageRegionIds() {
        StrapiQuestionDto question = buildQuestion("drag_numbers", null,
                List.of(new StrapiQuestionDto.ImageRegionDto(808, "Car A", 20, 70, 14, 16, false, "1"),
                        new StrapiQuestionDto.ImageRegionDto(809, "Car B", 60, 40, 16, 14, false, "2")),
                List.of(new StrapiQuestionDto.DragTargetDto(681, "Car A", "1", false, 0, null),
                        new StrapiQuestionDto.DragTargetDto(682, "Car B", "2", false, 1, null)));
        @SuppressWarnings("unchecked")
        Map<String, String> placements = (Map<String, String>) service.buildCorrectAnswer(question).get("placements");
        // Must use imageRegion IDs (808, 809), not dragTarget IDs (681, 682)
        assertThat(placements).containsEntry("808", "1").containsEntry("809", "2");
        assertThat(placements).doesNotContainKey("681");
    }

    // ─── Pattern B: imageRegions without correctValue, dragTargets carry it ───

    @Test
    void gradeAnswer_dragNumbers_imageRegionsWithoutCorrectValue_usesPositionalMapping() {
        // imageRegions define tap areas (no correctValue), dragTargets hold the ordering.
        // Frontend sends imageRegion IDs — grading maps positionally to dragTarget correctValues.
        StrapiQuestionDto question = buildQuestion("drag_numbers", null,
                List.of(new StrapiQuestionDto.ImageRegionDto(808, "Car A", 20, 70, 14, 16, false, null),
                        new StrapiQuestionDto.ImageRegionDto(809, "Car B", 60, 40, 16, 14, false, null),
                        new StrapiQuestionDto.ImageRegionDto(810, "Car C", 30, 80, 14, 16, false, null),
                        new StrapiQuestionDto.ImageRegionDto(811, "Pedestrian", 50, 20, 14, 16, false, null)),
                List.of(new StrapiQuestionDto.DragTargetDto(681, "Car A", "1", false, 0, null),
                        new StrapiQuestionDto.DragTargetDto(682, "Car B", "2", false, 1, null),
                        new StrapiQuestionDto.DragTargetDto(683, "Car C", "3", false, 2, null),
                        new StrapiQuestionDto.DragTargetDto(684, "Pedestrian", "4", false, 3, null)));
        // Correct: 808→1, 809→2, 810→3, 811→4
        assertThat(service.gradeAnswer(question, Map.of("placements", Map.of("808", "1", "809", "2", "810", "3", "811", "4")))).isTrue();
        // Wrong order
        assertThat(service.gradeAnswer(question, Map.of("placements", Map.of("808", "4", "809", "3", "810", "2", "811", "1")))).isFalse();
    }

    @Test
    void buildCorrectAnswer_dragNumbers_imageRegionsWithoutCorrectValue_mapsPositionally() {
        StrapiQuestionDto question = buildQuestion("drag_numbers", null,
                List.of(new StrapiQuestionDto.ImageRegionDto(808, "Car A", 20, 70, 14, 16, false, null),
                        new StrapiQuestionDto.ImageRegionDto(809, "Car B", 60, 40, 16, 14, false, null)),
                List.of(new StrapiQuestionDto.DragTargetDto(681, "Car A", "1", false, 0, null),
                        new StrapiQuestionDto.DragTargetDto(682, "Car B", "2", false, 1, null)));
        @SuppressWarnings("unchecked")
        Map<String, String> placements = (Map<String, String>) service.buildCorrectAnswer(question).get("placements");
        // Uses imageRegion IDs (808, 809) mapped to dragTarget correctValues (1, 2)
        assertThat(placements).containsEntry("808", "1").containsEntry("809", "2");
        assertThat(placements).doesNotContainKey("681");
    }

    // ─── MASTERY TRANSITIONS ───

    @Test
    void updateMastery_newQuestion_correctAnswer_becomesLearning() {
        QuestionProgress progress = createProgress(MasteryLevel.NEW, 0);
        service.updateMastery(progress, true);
        assertThat(progress.getMasteryLevel()).isEqualTo(MasteryLevel.LEARNING);
        assertThat(progress.getConsecutiveCorrect()).isEqualTo(1);
    }

    @Test
    void updateMastery_newQuestion_wrongAnswer_becomesLearning() {
        QuestionProgress progress = createProgress(MasteryLevel.NEW, 0);
        service.updateMastery(progress, false);
        assertThat(progress.getMasteryLevel()).isEqualTo(MasteryLevel.LEARNING);
        assertThat(progress.getConsecutiveCorrect()).isEqualTo(0);
    }

    @Test
    void updateMastery_learning_twoCorrect_becomesFamiliar() {
        QuestionProgress progress = createProgress(MasteryLevel.LEARNING, 1);
        service.updateMastery(progress, true);
        assertThat(progress.getMasteryLevel()).isEqualTo(MasteryLevel.FAMILIAR);
        assertThat(progress.getConsecutiveCorrect()).isEqualTo(2);
    }

    @Test
    void updateMastery_familiar_fourCorrect_becomesMastered() {
        QuestionProgress progress = createProgress(MasteryLevel.FAMILIAR, 3);
        service.updateMastery(progress, true);
        assertThat(progress.getMasteryLevel()).isEqualTo(MasteryLevel.MASTERED);
        assertThat(progress.getConsecutiveCorrect()).isEqualTo(4);
    }

    @Test
    void updateMastery_mastered_wrongAnswer_dropsFamiliar() {
        QuestionProgress progress = createProgress(MasteryLevel.MASTERED, 5);
        service.updateMastery(progress, false);
        assertThat(progress.getMasteryLevel()).isEqualTo(MasteryLevel.FAMILIAR);
        assertThat(progress.getConsecutiveCorrect()).isEqualTo(0);
    }

    @Test
    void updateMastery_familiar_wrongAnswer_dropsLearning() {
        QuestionProgress progress = createProgress(MasteryLevel.FAMILIAR, 2);
        service.updateMastery(progress, false);
        assertThat(progress.getMasteryLevel()).isEqualTo(MasteryLevel.LEARNING);
        assertThat(progress.getConsecutiveCorrect()).isEqualTo(0);
    }

    @Test
    void updateMastery_easeFactor_increases_onCorrect() {
        QuestionProgress progress = createProgress(MasteryLevel.LEARNING, 0);
        BigDecimal before = progress.getEaseFactor();
        service.updateMastery(progress, true);
        assertThat(progress.getEaseFactor()).isGreaterThan(before);
    }

    @Test
    void updateMastery_easeFactor_decreases_onWrong() {
        QuestionProgress progress = createProgress(MasteryLevel.LEARNING, 1);
        BigDecimal before = progress.getEaseFactor();
        service.updateMastery(progress, false);
        assertThat(progress.getEaseFactor()).isLessThan(before);
    }

    @Test
    void updateMastery_easeFactor_neverBelowMinimum() {
        QuestionProgress progress = createProgress(MasteryLevel.LEARNING, 0);
        progress.setEaseFactor(new BigDecimal("1.30"));
        service.updateMastery(progress, false);
        assertThat(progress.getEaseFactor()).isGreaterThanOrEqualTo(new BigDecimal("1.30"));
    }

    @Test
    void updateMastery_mastered_intervalCappedAt14Days() {
        QuestionProgress progress = createProgress(MasteryLevel.MASTERED, 5);
        progress.setIntervalDays(10);
        progress.setEaseFactor(new BigDecimal("2.50"));
        service.updateMastery(progress, true);
        assertThat(progress.getIntervalDays()).isLessThanOrEqualTo(14);
    }

    @Test
    void updateMastery_totalAttempts_incrementsOnEveryAnswer() {
        QuestionProgress progress = createProgress(MasteryLevel.NEW, 0);
        assertThat(progress.getTotalAttempts()).isEqualTo(0);
        service.updateMastery(progress, true);
        assertThat(progress.getTotalAttempts()).isEqualTo(1);
        service.updateMastery(progress, false);
        assertThat(progress.getTotalAttempts()).isEqualTo(2);
    }

    // ─── HELPERS ───

    private QuestionProgress createProgress(MasteryLevel level, int consecutiveCorrect) {
        QuestionProgress qp = new QuestionProgress(UUID.randomUUID(), "q-001", "auto-b", "domain1", "topic1");
        qp.setMasteryLevel(level);
        qp.setConsecutiveCorrect(consecutiveCorrect);
        qp.setEaseFactor(new BigDecimal("2.50"));
        qp.setIntervalDays(level == MasteryLevel.MASTERED ? 7 : 0);
        return qp;
    }

    private StrapiQuestionDto buildQuestion(String interactionType,
                                            List<StrapiQuestionDto.AnswerOptionDto> answerOptions,
                                            List<StrapiQuestionDto.ImageRegionDto> imageRegions,
                                            List<StrapiQuestionDto.DragTargetDto> dragTargets) {
        return new StrapiQuestionDto(1, "doc-001", "Test question", interactionType, "medium",
                null, null, null, null, 1, false, false, null, null,
                answerOptions, null, imageRegions, dragTargets, null, null, null, null, null);
    }

    private StrapiQuestionDto buildYesNoQuestion(Boolean correctBoolean) {
        return new StrapiQuestionDto(1, "doc-001", "Test yes/no question", "yes_no", "medium",
                null, null, null, correctBoolean, 1, false, false, null, null,
                null, null, null, null, null, null, null, null, null);
    }

    private StrapiQuestionDto buildFillInNumberQuestion(int correctNumber, int tolerance) {
        return new StrapiQuestionDto(1, "doc-001", "What number?", "fill_in_number", "medium",
                null, correctNumber, tolerance, null, 1, false, false, null, null,
                null, null, null, null, null, null, null, null, null);
    }
}
