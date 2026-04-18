package com.passtheo.content.unit;

import com.passtheo.content.controller.StudyPlanController;
import com.passtheo.content.dto.request.GenerateStudyPlanRequest;
import com.passtheo.content.dto.response.StudyPlanDto;
import com.passtheo.content.service.StudyPlanService;
import com.passtheo.shared.core.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for StudyPlanController — focus on the preview endpoint and its
 * contract that a preview response carries {@code planId=null} +
 * {@code status="PREVIEW"}.
 */
@ExtendWith(MockitoExtension.class)
class StudyPlanControllerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private StudyPlanService studyPlanService;

    private StudyPlanController controller;

    @BeforeEach
    void setUp() {
        controller = new StudyPlanController(studyPlanService);
    }

    @Test
    void previewStudyPlan_returns200WithPreviewDto() {
        GenerateStudyPlanRequest request = new GenerateStudyPlanRequest(
                "motor-a", LocalDate.of(2026, 9, 1), null);
        StudyPlanDto preview = new StudyPlanDto(
                null,
                "motor-a",
                LocalDate.of(2026, 9, 1),
                60, 0, 12,
                "PREVIEW",
                List.of("signs", "priority"),
                List.of());
        when(studyPlanService.previewPlan(USER_ID, request, "nl")).thenReturn(preview);

        ResponseEntity<ApiResponse<StudyPlanDto>> response =
                controller.previewStudyPlan(USER_ID, request, "nl");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        StudyPlanDto data = response.getBody().getData();
        assertThat(data.planId()).isNull();
        assertThat(data.status()).isEqualTo("PREVIEW");
        assertThat(data.productCode()).isEqualTo("motor-a");
        assertThat(data.totalDays()).isEqualTo(60);
        assertThat(data.dailyQuestionTarget()).isEqualTo(12);
        verify(studyPlanService).previewPlan(USER_ID, request, "nl");
    }

    @Test
    void previewStudyPlan_passesLocaleThrough() {
        GenerateStudyPlanRequest request = new GenerateStudyPlanRequest(
                "auto-b", LocalDate.of(2026, 7, 1), null);
        StudyPlanDto preview = new StudyPlanDto(
                null, "auto-b", LocalDate.of(2026, 7, 1),
                60, 0, 10, "PREVIEW", List.of(), List.of());
        when(studyPlanService.previewPlan(USER_ID, request, "en")).thenReturn(preview);

        controller.previewStudyPlan(USER_ID, request, "en");

        verify(studyPlanService).previewPlan(USER_ID, request, "en");
    }
}
