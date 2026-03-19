package com.passtheo.content.kafka.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.passtheo.content.repository.DomainProgressRepository;
import com.passtheo.content.repository.EarnedAchievementRepository;
import com.passtheo.content.repository.ExamAttemptRepository;
import com.passtheo.content.repository.QuestionProgressRepository;
import com.passtheo.content.repository.ReadinessSnapshotRepository;
import com.passtheo.content.repository.StreakRepository;
import com.passtheo.content.repository.StudyPlanRepository;
import com.passtheo.content.repository.TopicProgressRepository;
import jakarta.annotation.Nonnull;
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

    private final ObjectMapper objectMapper;
    private final QuestionProgressRepository progressRepository;
    private final TopicProgressRepository topicProgressRepository;
    private final DomainProgressRepository domainProgressRepository;
    private final StreakRepository streakRepository;
    private final EarnedAchievementRepository achievementRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final ReadinessSnapshotRepository snapshotRepository;
    private final StudyPlanRepository planRepository;

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
     */
    public UserEventConsumer(ObjectMapper objectMapper,
                             QuestionProgressRepository progressRepository,
                             TopicProgressRepository topicProgressRepository,
                             DomainProgressRepository domainProgressRepository,
                             StreakRepository streakRepository,
                             EarnedAchievementRepository achievementRepository,
                             ExamAttemptRepository examAttemptRepository,
                             ReadinessSnapshotRepository snapshotRepository,
                             StudyPlanRepository planRepository) {
        this.objectMapper = objectMapper;
        this.progressRepository = progressRepository;
        this.topicProgressRepository = topicProgressRepository;
        this.domainProgressRepository = domainProgressRepository;
        this.streakRepository = streakRepository;
        this.achievementRepository = achievementRepository;
        this.examAttemptRepository = examAttemptRepository;
        this.snapshotRepository = snapshotRepository;
        this.planRepository = planRepository;
    }

    /**
     * Handles user events from Kafka.
     *
     * @param record the Kafka record
     * @param ack    the acknowledgment
     */
    @KafkaListener(topics = "passtheo.user", groupId = "passtheo-content-service")
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
            }

            ack.acknowledge();
        } catch (Exception ex) {
            LOG.error("Failed to process user event: offset={}", record.offset(), ex);
        }
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
