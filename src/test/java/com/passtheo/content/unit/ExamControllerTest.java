package com.passtheo.content.unit;

import com.passtheo.shared.core.dto.AccessGrant;
import com.passtheo.content.dto.request.StartExamRequest;
import com.passtheo.content.dto.response.ExamConfigPreviewDto;
import com.passtheo.content.dto.response.ExamDto;
import com.passtheo.content.service.EntitlementChecker;
import com.passtheo.content.service.MockExamService;
import com.passtheo.content.controller.ExamController;
import com.passtheo.shared.core.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ExamController — free tier blocking.
 */
@ExtendWith(MockitoExtension.class)
class ExamControllerTest {

    @Mock private MockExamService mockExamService;
    @Mock private EntitlementChecker entitlementChecker;

    private ExamController controller;

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new ExamController(mockExamService, entitlementChecker);
    }

    @Test
    void startMockExam_freeUser_returns403PremiumRequired() {
        when(entitlementChecker.canStartExam(TENANT_ID, USER_ID)).thenReturn(false);

        StartExamRequest request = new StartExamRequest("auto-b", "nl");
        ResponseEntity<?> response = controller.startMockExam(TENANT_ID, USER_ID, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);
        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertThat(problem.getTitle()).isEqualTo("Premium required");
        assertThat(problem.getDetail()).isEqualTo("Upgrade to Pro to take mock exams.");
        assertThat(problem.getType().toString()).isEqualTo("https://api.passtheo.nl/errors/premium-required");

        verify(mockExamService, never()).startExam(any(), any());
    }

    @Test
    void startMockExam_paidUser_proceedsNormally() {
        when(entitlementChecker.canStartExam(TENANT_ID, USER_ID)).thenReturn(true);
        ExamDto examDto = new ExamDto(UUID.randomUUID(), 50, 1800, 44,
                java.time.Instant.now(), java.time.Instant.now().plusSeconds(1800), List.of());
        when(mockExamService.startExam(any(), any())).thenReturn(examDto);

        StartExamRequest request = new StartExamRequest("auto-b", "nl");
        ResponseEntity<?> response = controller.startMockExam(TENANT_ID, USER_ID, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(mockExamService).startExam(USER_ID, request);
    }

    @Test
    void getExamConfig_returnsPreview() {
        ExamConfigPreviewDto preview = new ExamConfigPreviewDto(
                "Theory Exam",
                "Simulate the real theory exam experience under authentic conditions.",
                50, 30, 44,
                List.of("Rule 1", "Rule 2")
        );
        when(mockExamService.getExamConfigPreview("auto-b", "en")).thenReturn(preview);

        ResponseEntity<ApiResponse<ExamConfigPreviewDto>> response =
                controller.getExamConfig("auto-b", "en");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().title()).isEqualTo("Theory Exam");
        assertThat(response.getBody().getData().totalQuestions()).isEqualTo(50);
        assertThat(response.getBody().getData().rules()).hasSize(2);
        verify(mockExamService).getExamConfigPreview("auto-b", "en");
    }

    @Test
    void getExamConfig_noLocaleParam_usesDefaultNl() {
        ExamConfigPreviewDto preview = new ExamConfigPreviewDto(
                "Theorie-examen",
                "Simuleer het echte theorie-examen.",
                50, 30, 44,
                List.of("Regel 1")
        );
        when(mockExamService.getExamConfigPreview("auto-b", "nl")).thenReturn(preview);

        ResponseEntity<ApiResponse<ExamConfigPreviewDto>> response =
                controller.getExamConfig("auto-b", "nl");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().title()).isEqualTo("Theorie-examen");
        verify(mockExamService).getExamConfigPreview("auto-b", "nl");
    }
}
