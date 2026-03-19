package com.passtheo.content.outbox;

import com.passtheo.content.domain.entity.OutboxEvent;
import com.passtheo.content.domain.enums.OutboxStatus;
import com.passtheo.content.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Polls the outbox_events table and publishes pending events to Kafka.
 * Runs on a fixed interval using Spring @Scheduled.
 */
@Component
public class OutboxPoller {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Constructor injection.
     *
     * @param outboxEventRepository the outbox event repository
     * @param kafkaTemplate         the Kafka template
     */
    public OutboxPoller(OutboxEventRepository outboxEventRepository,
                        KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Polls for pending outbox events and publishes them to Kafka.
     */
    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:5000}")
    @Transactional
    public void pollAndPublish() {
        List<OutboxEvent> pending = outboxEventRepository
                .findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getTenantId().toString(), event.getPayload())
                        .whenComplete((result, ex) -> {
                            if (ex != null) {
                                LOG.error("Failed to send outbox event {} to Kafka: {}",
                                        event.getId(), ex.getMessage());
                                event.markFailed(ex.getMessage());
                                outboxEventRepository.save(event);
                            }
                        });

                event.markSent();
                outboxEventRepository.save(event);
                LOG.debug("Published outbox event {} to topic {}", event.getId(), event.getTopic());
            } catch (Exception e) {
                LOG.error("Failed to publish outbox event {}: {}", event.getId(), e.getMessage(), e);
                event.markFailed(e.getMessage());
                outboxEventRepository.save(event);
            }
        }
    }
}
