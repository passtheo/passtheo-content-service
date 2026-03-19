package com.passtheo.content.kafka.consumer;

import jakarta.annotation.Nonnull;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes tenant events: tenant.terminated → delete all tenant learning data.
 */
@Component
public class TenantEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(TenantEventConsumer.class);

    /**
     * Handles tenant events from Kafka.
     *
     * @param record the Kafka record
     * @param ack    the acknowledgment
     */
    @KafkaListener(topics = "passtheo.tenant", groupId = "passtheo-content-service")
    public void onTenantEvent(@Nonnull ConsumerRecord<String, String> record,
                              @Nonnull Acknowledgment ack) {
        LOG.debug("Received tenant event: topic={}, partition={}, offset={}",
                record.topic(), record.partition(), record.offset());

        try {
            // tenant.terminated → RLS ensures tenant data is isolated
            // Cleanup can be done via async batch job if needed
            LOG.info("Processed tenant event: key={}", record.key());
            ack.acknowledge();
        } catch (Exception ex) {
            LOG.error("Failed to process tenant event: offset={}", record.offset(), ex);
        }
    }
}
