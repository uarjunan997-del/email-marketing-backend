package com.emailMarketing.streaming;

import org.springframework.stereotype.Service;
import org.springframework.kafka.core.KafkaTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;
import java.util.UUID;

@Service
public class EmailEventProducer {
    private static final Logger log = LoggerFactory.getLogger(EmailEventProducer.class);
    private final KafkaTemplate<String, Object> kafka;

    public EmailEventProducer(KafkaTemplate<String, Object> kafka) {
        this.kafka = kafka;
    }

    public void publish(EmailEventPayload payload) {
        if (payload.getEventId() == null) {
            payload.setEventId(UUID.randomUUID().toString());
        }
        if (payload.getOccurredAt() == null) {
            payload.setOccurredAt(Instant.now());
        }
        kafka.send(KafkaTopics.EMAIL_EVENTS, payload.getEventId(), payload);
        log.debug("published_email_event id={} type={}", payload.getEventId(), payload.getType());
    }
}
