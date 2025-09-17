package com.emailMarketing.streaming;

import org.springframework.stereotype.Component;
import org.springframework.kafka.annotation.KafkaListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@org.springframework.context.annotation.Profile("kafka")
public class DlqEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(DlqEventConsumer.class);

    @KafkaListener(topics = KafkaTopics.DLQ_EVENTS, groupId = "analytics-dlq")
    public void onDlq(Object payload) {
        log.error("DLQ event captured class={} payload={}",
                payload == null ? "null" : payload.getClass().getSimpleName(), payload);
        // Future: persist to a DLQ table for manual replay
    }
}