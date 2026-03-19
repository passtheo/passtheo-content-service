package com.passtheo.content.kafka.consumer;

import jakarta.annotation.Nonnull;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes subscription events: subscription.activated, subscription.expired.
 */
@Component
public class SubscriptionEventConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionEventConsumer.class);

    /**
     * Handles subscription events from Kafka.
     *
     * @param record the Kafka record
     * @param ack    the acknowledgment
     */
    @KafkaListener(topics = "passtheo.subscription", groupId = "passtheo-content-service")
    public void onSubscriptionEvent(@Nonnull ConsumerRecord<String, String> record,
                                    @Nonnull Acknowledgment ack) {
        LOG.debug("Received subscription event: topic={}, partition={}, offset={}",
                record.topic(), record.partition(), record.offset());

        try {
            // subscription.activated → warm Redis cache with entitlements (handled by subscription-service)
            // subscription.expired → restrict access (handled by subscription-service writing Redis)
            // Content service only needs to be aware for potential cache warming
            LOG.info("Processed subscription event: key={}", record.key());
            ack.acknowledge();
        } catch (Exception ex) {
            LOG.error("Failed to process subscription event: offset={}", record.offset(), ex);
        }
    }
}
