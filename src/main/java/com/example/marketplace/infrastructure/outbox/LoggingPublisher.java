package com.example.marketplace.infrastructure.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Logs events instead of sending them anywhere. Replace it with a Kafka, NATS
 * or SQS publisher in a real deployment.
 */
@Component
class LoggingPublisher implements Publisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingPublisher.class);

    @Override
    public void publish(String eventName, String payload) {
        log.info("publishing domain event event_name={} payload={}", eventName, payload);
    }
}
