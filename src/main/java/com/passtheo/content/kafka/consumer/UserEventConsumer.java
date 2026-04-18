package com.passtheo.content.kafka.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.passtheo.content.domain.enums.PlanStatus;
import com.passtheo.content.dto.request.GenerateStudyPlanRequest;
import com.passtheo.content.repository.DomainProgressRepository;
import com.passtheo.content.repository.EarnedAchievementRepository;
import com.passtheo.content.repository.ExamAttemptRepository;
import com.passtheo.content.repository.QuestionProgressRepository;
import com.passtheo.content.repository.ReadinessSnapshotRepository;
import com.passtheo.content.repository.StreakRepository;
import com.passtheo.content.repository.StudyPlanRepository;
import com.passtheo.content.repository.TopicProgressRepository;
import com.passtheo.content.service.StudyPlanService;
import com.passtheo.shared.core.context.TenantContext;
import com.passtheo.shared.events.config.KafkaTopic;
import jakarta.annotation.Nonnull;

import java.time.LocalDate;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consumes user events: user.deleted → GDPR delete all learning data.
 */
@Component
public class UserEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(UserEventConsumer.class);

    /** Default locale for auto-generated study plans. */
    private static final String DEFAULT_LOCALE = "nl";

    private final ObjectMapper objectMapper;
    private final QuestionProgressRepository progressRepository;
    private final TopicProgressRepository topicProgressRepository;
    private final DomainProgressRepository domainProgressRepository;
    private final StreakRepository streakRepository;
    private final EarnedAchievementRepository achievementRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final ReadinessSnapshotRepository snapshotRepository;
    private final StudyPlanRepository planRepository;
    private final StudyPlanService studyPlanService;

    /**
     * Constructs the user event consumer.
     *
     * @param objectMapper             JSON mapper
     * @param progressRepository       question progress repository
     * @param topicProgressRepository  topic progress repository
     * @param domainProgressRepository domain progress repository
     * @param streakRepository         streak repository
     * @param achievementRepository    earned achievement repository
     * @param examAttemptRepository    exam attempt repository
     * @param snapshotRepository       readiness snapshot repository
     * @param planRepository           study plan repository
     * @param studyPlanService         study plan service for auto-generation
     */
    public UserEventConsumer(ObjectMapper objectMapper,
                             QuestionProgressRepository progressRepository,
                             TopicProgressRepository topicProgressRepository,
                             DomainProgressRepository domainProgressRepository,
                             StreakRepository streakRepository,
                             EarnedAchievementRepository achievementRepository,
                             ExamAttemptRepository examAttemptRepository,
                             ReadinessSnapshotRepository snapshotRepository,
                             StudyPlanRepository planRepository,
                             StudyPlanService studyPlanService) {
        this.objectMapper = objectMapper;
        this.progressRepository = progressRepository;
        this.topicProgressRepository = topicProgressRepository;
        this.domainProgressRepository = domainProgressRepository;
        this.streakRepository = streakRepository;
        this.achievementRepository = achievementRepository;
        this.examAttemptRepository = examAttemptRepository;
        this.snapshotRepository = snapshotRepository;
        this.planRepository = planRepository;
        this.studyPlanService = studyPlanService;
    }

    /**
     * Handles user events from Kafka.
     *
     * @param record the Kafka record
     * @param ack    the acknowledgment
     */
    @KafkaListener(topics = KafkaTopic.USER_EVENTS, groupId = "passtheo-content-service")
    @Transactional
    public void onUserEvent(@Nonnull ConsumerRecord<String, String> record,
                            @Nonnull Acknowledgment ack) {
        LOG.debug("Received user event: topic={}, partition={}, offset={}",
                record.topic(), record.partition(), record.offset());

        try {
            JsonNode payload = objectMapper.readTree(record.value());
            String eventType = payload.has("eventType") ? payload.get("eventType").asText() : "";

            if ("UserDeletedEvent".equals(eventType) || "user.deleted".equals(eventType)) {
                String userIdStr = payload.has("keycloakUserId")
                        ? payload.get("keycloakUserId").asText()
                        : payload.get("userId").asText();
                UUID userId = UUID.fromString(userIdStr);

                LOG.info("GDPR delete triggered by user.deleted event: userId={}", userId);
                deleteAllUserData(userId);

            } else if ("UserOnboardingCompleted".equals(eventType)) {
                handleOnboardingCompleted(payload, record);

            } else if ("UserExamDateSet".equals(eventType)) {
                handleExamDateSet(payload, record);

            } else if ("UserExamDateCleared".equals(eventType)) {
                handleExamDateCleared(payload, record);

            } else if ("UserProductChanged".equals(eventType)) {
                handleProductChanged(payload, record);
            }

            ack.acknowledge();
        } catch (Exception ex) {
            LOG.error("Failed to process user event: offset={}", record.offset(), ex);
        }
    }

    private void handleOnboardingCompleted(JsonNode payload, ConsumerRecord<String, String> record) {
        String keycloakUserIdStr = payload.has("keycloakUserId")
                ? payload.get("keycloakUserId").asText(null) : null;
        String tenantIdStr = payload.has("tenantId")
                ? payload.get("tenantId").asText(null) : null;
        String productCode = payload.has("productCode")
                ? payload.get("productCode").asText(null) : null;

        if (keycloakUserIdStr == null || tenantIdStr == null
                || productCode == null || productCode.isBlank()) {
            LOG.warn("Ignoring incomplete UserOnboardingCompleted event: offset={}", record.offset());
            return;
        }

        UUID keycloakUserId = UUID.fromString(keycloakUserIdStr);
        UUID tenantId = UUID.fromString(tenantIdStr);
        LocalDate examDate = parseLocalDateNode(payload.get("examDate"));

        boolean hasActivePlan = planRepository.findByKeycloakUserIdAndProductCodeAndStatus(
                keycloakUserId, productCode, PlanStatus.ACTIVE).isPresent();

        if (!hasActivePlan) {
            LOG.info("Auto-generating study plan: userId={}, productCode={}, examDate={}",
                    keycloakUserId, productCode, examDate);
            TenantContext.set(tenantId);
            try {
                studyPlanService.generatePlan(keycloakUserId,
                        new GenerateStudyPlanRequest(productCode, examDate, null), DEFAULT_LOCALE);
            } finally {
                TenantContext.clear();
            }
        } else {
            LOG.debug("Skipping auto-generate: active plan exists for userId={}, productCode={}",
                    keycloakUserId, productCode);
        }
    }

    private void handleExamDateSet(JsonNode payload, ConsumerRecord<String, String> record) {
        String keycloakUserIdStr = payload.has("keycloakUserId")
                ? payload.get("keycloakUserId").asText(null) : null;
        String tenantIdStr = payload.has("tenantId")
                ? payload.get("tenantId").asText(null) : null;
        String productCode = payload.has("productCode")
                ? payload.get("productCode").asText(null) : null;

        if (keycloakUserIdStr == null || tenantIdStr == null
                || productCode == null || productCode.isBlank()) {
            LOG.warn("Ignoring incomplete UserExamDateSet event: offset={}", record.offset());
            return;
        }

        UUID keycloakUserId = UUID.fromString(keycloakUserIdStr);
        UUID tenantId = UUID.fromString(tenantIdStr);
        LocalDate examDate = parseLocalDateNode(payload.get("examDate"));

        LOG.info("Regenerating study plan for exam date change: userId={}, productCode={}, examDate={}",
                keycloakUserId, productCode, examDate);
        TenantContext.set(tenantId);
        try {
            studyPlanService.generatePlan(keycloakUserId,
                    new GenerateStudyPlanRequest(productCode, examDate, null), DEFAULT_LOCALE);
        } finally {
            TenantContext.clear();
        }
    }

    private void handleExamDateCleared(JsonNode payload, ConsumerRecord<String, String> record) {
        String keycloakUserIdStr = payload.has("keycloakUserId")
                ? payload.get("keycloakUserId").asText(null) : null;
        String tenantIdStr = payload.has("tenantId")
                ? payload.get("tenantId").asText(null) : null;
        String productCode = payload.has("productCode")
                ? payload.get("productCode").asText(null) : null;

        if (keycloakUserIdStr == null || tenantIdStr == null
                || productCode == null || productCode.isBlank()) {
            LOG.warn("Ignoring incomplete UserExamDateCleared event: offset={}", record.offset());
            return;
        }

        UUID keycloakUserId = UUID.fromString(keycloakUserIdStr);
        UUID tenantId = UUID.fromString(tenantIdStr);

        LOG.info("Abandoning study plan after exam date cleared: userId={}, productCode={}",
                keycloakUserId, productCode);
        TenantContext.set(tenantId);
        try {
            studyPlanService.abandonActivePlan(keycloakUserId, productCode);
        } finally {
            TenantContext.clear();
        }
    }

    private void handleProductChanged(JsonNode payload, ConsumerRecord<String, String> record) {
        String keycloakUserIdStr = payload.has("keycloakUserId")
                ? payload.get("keycloakUserId").asText(null) : null;
        String tenantIdStr = payload.has("tenantId")
                ? payload.get("tenantId").asText(null) : null;
        String oldProductCode = payload.has("oldProductCode")
                ? payload.get("oldProductCode").asText(null) : null;
        String newProductCode = payload.has("newProductCode")
                ? payload.get("newProductCode").asText(null) : null;
        if (keycloakUserIdStr == null || tenantIdStr == null
                || oldProductCode == null || oldProductCode.isBlank()
                || newProductCode == null || newProductCode.isBlank()) {
            LOG.warn("Ignoring incomplete UserProductChanged event: offset={}", record.offset());
            return;
        }

        UUID keycloakUserId = UUID.fromString(keycloakUserIdStr);
        UUID tenantId = UUID.fromString(tenantIdStr);
        LocalDate examDate = parseLocalDateNode(payload.get("examDate"));

        LOG.info("Product changed: userId={}, old={}, new={}, examDate={}",
                keycloakUserId, oldProductCode, newProductCode, examDate);

        TenantContext.set(tenantId);
        try {
            studyPlanService.abandonActivePlan(keycloakUserId, oldProductCode);
            if (examDate != null) {
                studyPlanService.generatePlan(keycloakUserId,
                        new GenerateStudyPlanRequest(newProductCode, examDate, null), DEFAULT_LOCALE);
            } else {
                LOG.info("No exam date on new product — plan will be generated when user sets one: "
                        + "userId={}, newProductCode={}", keycloakUserId, newProductCode);
            }
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Parses a LocalDate from a JSON node, tolerating both the ISO string format
     * (e.g. {@code "2026-05-01"}) and the legacy Jackson timestamp-array format
     * (e.g. {@code [2026, 5, 1]}) that older OutboxEventPublisher versions
     * emitted before {@code WRITE_DATES_AS_TIMESTAMPS=false} was applied. Any
     * pending outbox rows written under the old format must still deserialize
     * correctly when the poller replays them.
     *
     * @param node the JSON node (may be null or a null node)
     * @return the parsed LocalDate, or null if the node is absent / null / empty
     */
    private static LocalDate parseLocalDateNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isArray() && node.size() == 3) {
            return LocalDate.of(node.get(0).asInt(), node.get(1).asInt(), node.get(2).asInt());
        }
        String text = node.asText(null);
        return (text == null || text.isEmpty()) ? null : LocalDate.parse(text);
    }

    private void deleteAllUserData(UUID userId) {
        progressRepository.deleteByKeycloakUserId(userId);
        topicProgressRepository.deleteByKeycloakUserId(userId);
        domainProgressRepository.deleteByKeycloakUserId(userId);
        streakRepository.deleteByKeycloakUserId(userId);
        achievementRepository.deleteByKeycloakUserId(userId);
        examAttemptRepository.deleteByKeycloakUserId(userId);
        snapshotRepository.deleteByKeycloakUserId(userId);
        planRepository.deleteByKeycloakUserId(userId);
        LOG.info("GDPR delete complete: userId={}", userId);
    }
}
