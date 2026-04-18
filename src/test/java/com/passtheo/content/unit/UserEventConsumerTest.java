package com.passtheo.content.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.passtheo.content.dto.request.GenerateStudyPlanRequest;
import com.passtheo.content.kafka.consumer.UserEventConsumer;
import com.passtheo.content.repository.DomainProgressRepository;
import com.passtheo.content.repository.EarnedAchievementRepository;
import com.passtheo.content.repository.ExamAttemptRepository;
import com.passtheo.content.repository.QuestionProgressRepository;
import com.passtheo.content.repository.ReadinessSnapshotRepository;
import com.passtheo.content.repository.StreakRepository;
import com.passtheo.content.repository.StudyPlanRepository;
import com.passtheo.content.repository.TopicProgressRepository;
import com.passtheo.content.service.StudyPlanService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for UserEventConsumer — focuses on the UserProductChanged branch.
 */
@ExtendWith(MockitoExtension.class)
class UserEventConsumerTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private QuestionProgressRepository progressRepository;
    @Mock private TopicProgressRepository topicProgressRepository;
    @Mock private DomainProgressRepository domainProgressRepository;
    @Mock private StreakRepository streakRepository;
    @Mock private EarnedAchievementRepository achievementRepository;
    @Mock private ExamAttemptRepository examAttemptRepository;
    @Mock private ReadinessSnapshotRepository snapshotRepository;
    @Mock private StudyPlanRepository planRepository;
    @Mock private StudyPlanService studyPlanService;
    @Mock private Acknowledgment ack;

    private UserEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new UserEventConsumer(objectMapper,
                progressRepository, topicProgressRepository, domainProgressRepository,
                streakRepository, achievementRepository, examAttemptRepository,
                snapshotRepository, planRepository, studyPlanService);
    }

    @Test
    void onUserProductChanged_abandonsOldAndRegeneratesWhenExamDatePresent() {
        String payload = """
                {
                  "eventType": "UserProductChanged",
                  "tenantId": "%s",
                  "keycloakUserId": "%s",
                  "oldProductCode": "auto-b",
                  "newProductCode": "motor-a",
                  "examDate": "2026-09-01"
                }
                """.formatted(TENANT_ID, USER_ID);
        ConsumerRecord<String, String> record = kafkaRecord(payload);

        consumer.onUserEvent(record, ack);

        verify(studyPlanService).abandonActivePlan(USER_ID, "auto-b");

        ArgumentCaptor<GenerateStudyPlanRequest> requestCaptor =
                ArgumentCaptor.forClass(GenerateStudyPlanRequest.class);
        verify(studyPlanService).generatePlan(eq(USER_ID), requestCaptor.capture(), eq("nl"));
        GenerateStudyPlanRequest captured = requestCaptor.getValue();
        assertThat(captured.productCode()).isEqualTo("motor-a");
        assertThat(captured.examDate()).isEqualTo(LocalDate.of(2026, 9, 1));

        verify(ack).acknowledge();
    }

    @Test
    void onUserProductChanged_abandonsButSkipsRegenWhenExamDateNull() {
        String payload = """
                {
                  "eventType": "UserProductChanged",
                  "tenantId": "%s",
                  "keycloakUserId": "%s",
                  "oldProductCode": "auto-b",
                  "newProductCode": "motor-a",
                  "examDate": null
                }
                """.formatted(TENANT_ID, USER_ID);
        ConsumerRecord<String, String> record = kafkaRecord(payload);

        consumer.onUserEvent(record, ack);

        verify(studyPlanService).abandonActivePlan(USER_ID, "auto-b");
        verify(studyPlanService, never()).generatePlan(any(), any(), any());
        verify(ack).acknowledge();
    }

    @Test
    void onUserProductChanged_missingOldProductCode_isIgnoredButAcked() {
        String payload = """
                {
                  "eventType": "UserProductChanged",
                  "tenantId": "%s",
                  "keycloakUserId": "%s",
                  "newProductCode": "motor-a",
                  "examDate": "2026-09-01"
                }
                """.formatted(TENANT_ID, USER_ID);
        ConsumerRecord<String, String> record = kafkaRecord(payload);

        consumer.onUserEvent(record, ack);

        verify(studyPlanService, never()).abandonActivePlan(any(), any());
        verify(studyPlanService, never()).generatePlan(any(), any(), any());
        verify(ack).acknowledge();
    }

    @Test
    void onUserProductChanged_examDateInLegacyArrayFormat_isParsedCorrectly() {
        // Verifies backward compat with outbox rows written before
        // WRITE_DATES_AS_TIMESTAMPS=false was applied to OutboxEventPublisher.
        String payload = """
                {
                  "eventType": "UserProductChanged",
                  "tenantId": "%s",
                  "keycloakUserId": "%s",
                  "oldProductCode": "auto-b",
                  "newProductCode": "motor-a",
                  "examDate": [2026, 9, 1]
                }
                """.formatted(TENANT_ID, USER_ID);
        ConsumerRecord<String, String> record = kafkaRecord(payload);

        consumer.onUserEvent(record, ack);

        verify(studyPlanService).abandonActivePlan(USER_ID, "auto-b");
        ArgumentCaptor<GenerateStudyPlanRequest> requestCaptor =
                ArgumentCaptor.forClass(GenerateStudyPlanRequest.class);
        verify(studyPlanService).generatePlan(eq(USER_ID), requestCaptor.capture(), eq("nl"));
        assertThat(requestCaptor.getValue().examDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        verify(ack).acknowledge();
    }

    @Test
    void onUserProductChanged_exceptionInService_isSwallowed() {
        String payload = """
                {
                  "eventType": "UserProductChanged",
                  "tenantId": "%s",
                  "keycloakUserId": "%s",
                  "oldProductCode": "auto-b",
                  "newProductCode": "motor-a",
                  "examDate": "2026-09-01"
                }
                """.formatted(TENANT_ID, USER_ID);
        ConsumerRecord<String, String> record = kafkaRecord(payload);
        org.mockito.Mockito.doThrow(new RuntimeException("db down"))
                .when(studyPlanService).abandonActivePlan(any(), any());

        // Should not throw — the consumer catches and logs.
        consumer.onUserEvent(record, ack);

        verify(ack, never()).acknowledge();
    }

    private static ConsumerRecord<String, String> kafkaRecord(String value) {
        return new ConsumerRecord<>("passtheo.user.events", 0, 0L, "key", value);
    }
}
