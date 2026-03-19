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
 * Unit tests for AnswerProcessingService — grades all 6 interaction types
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
                List.of(new StrapiQuestionDto.AnswerOptionDto("a1", "Correct", null, true, 1),
                        new StrapiQuestionDto.AnswerOptionDto("a2", "Wrong", null, false, 2)),
                null, null);
        assertThat(service.gradeAnswer(question, Map.of("selectedOptionId", "a1"))).isTrue();
    }

    @Test
    void gradeAnswer_multipleChoice_wrongOption_returnsFalse() {
        StrapiQuestionDto question = buildQuestion("multiple_choice",
                List.of(new StrapiQuestionDto.AnswerOptionDto("a1", "Correct", null, true, 1),
                        new StrapiQuestionDto.AnswerOptionDto("a2", "Wrong", null, false, 2)),
                null, null);
        assertThat(service.gradeAnswer(question, Map.of("selectedOptionId", "a2"))).isFalse();
    }

    // ─── YES/NO ───

    @Test
    void gradeAnswer_yesNo_correctYes_returnsTrue() {
        StrapiQuestionDto question = buildQuestion("yes_no",
                List.of(new StrapiQuestionDto.AnswerOptionDto("a1", "Ja", null, true, 1),
                        new StrapiQuestionDto.AnswerOptionDto("a2", "Nee", null, false, 2)),
                null, null);
        assertThat(service.gradeAnswer(question, Map.of("answer", true))).isTrue();
    }

    @Test
    void gradeAnswer_yesNo_wrongAnswer_returnsFalse() {
        StrapiQuestionDto question = buildQuestion("yes_no",
                List.of(new StrapiQuestionDto.AnswerOptionDto("a1", "Ja", null, true, 1),
                        new StrapiQuestionDto.AnswerOptionDto("a2", "Nee", null, false, 2)),
                null, null);
        assertThat(service.gradeAnswer(question, Map.of("answer", false))).isFalse();
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
                List.of(new StrapiQuestionDto.ImageRegionDto("r1", "Region A", 10, 20, 30, 40, true, 1),
                        new StrapiQuestionDto.ImageRegionDto("r2", "Region B", 50, 60, 30, 40, false, 2)),
                null);
        assertThat(service.gradeAnswer(question, Map.of("tappedRegionId", "r1"))).isTrue();
    }

    @Test
    void gradeAnswer_tapOnImage_wrongRegion_returnsFalse() {
        StrapiQuestionDto question = buildQuestion("tap_on_image", null,
                List.of(new StrapiQuestionDto.ImageRegionDto("r1", "Region A", 10, 20, 30, 40, true, 1),
                        new StrapiQuestionDto.ImageRegionDto("r2", "Region B", 50, 60, 30, 40, false, 2)),
                null);
        assertThat(service.gradeAnswer(question, Map.of("tappedRegionId", "r2"))).isFalse();
    }

    // ─── DRAG CHECKMARK ───

    @Test
    void gradeAnswer_dragCheckmark_allCorrectTargets_returnsTrue() {
        StrapiQuestionDto question = buildQuestion("drag_checkmark", null, null,
                List.of(new StrapiQuestionDto.DragTargetDto("t1", "Target 1", null, true, 1, null),
                        new StrapiQuestionDto.DragTargetDto("t2", "Target 2", null, false, 2, null),
                        new StrapiQuestionDto.DragTargetDto("t3", "Target 3", null, true, 3, null)));
        assertThat(service.gradeAnswer(question, Map.of("selectedTargetIds", List.of("t1", "t3")))).isTrue();
    }

    @Test
    void gradeAnswer_dragCheckmark_missingTarget_returnsFalse() {
        StrapiQuestionDto question = buildQuestion("drag_checkmark", null, null,
                List.of(new StrapiQuestionDto.DragTargetDto("t1", "Target 1", null, true, 1, null),
                        new StrapiQuestionDto.DragTargetDto("t3", "Target 3", null, true, 3, null)));
        assertThat(service.gradeAnswer(question, Map.of("selectedTargetIds", List.of("t1")))).isFalse();
    }

    // ─── DRAG NUMBERS ───

    @Test
    void gradeAnswer_dragNumbers_correctOrder_returnsTrue() {
        StrapiQuestionDto question = buildQuestion("drag_numbers", null, null,
                List.of(new StrapiQuestionDto.DragTargetDto("t1", "Pos 1", "1", false, 1, null),
                        new StrapiQuestionDto.DragTargetDto("t2", "Pos 2", "2", false, 2, null)));
        assertThat(service.gradeAnswer(question, Map.of("placements", Map.of("t1", "1", "t2", "2")))).isTrue();
    }

    @Test
    void gradeAnswer_dragNumbers_wrongOrder_returnsFalse() {
        StrapiQuestionDto question = buildQuestion("drag_numbers", null, null,
                List.of(new StrapiQuestionDto.DragTargetDto("t1", "Pos 1", "1", false, 1, null),
                        new StrapiQuestionDto.DragTargetDto("t2", "Pos 2", "2", false, 2, null)));
        assertThat(service.gradeAnswer(question, Map.of("placements", Map.of("t1", "2", "t2", "1")))).isFalse();
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
        return new StrapiQuestionDto("q-001", "Test question", interactionType, "medium",
                null, null, null, null, 1, answerOptions, null, imageRegions, dragTargets,
                "domain1", "topic1");
    }

    private StrapiQuestionDto buildFillInNumberQuestion(int correctNumber, int tolerance) {
        return new StrapiQuestionDto("q-001", "What number?", "fill_in_number", "medium",
                null, null, correctNumber, tolerance, 1, null, null, null, null,
                "domain1", "topic1");
    }
}
