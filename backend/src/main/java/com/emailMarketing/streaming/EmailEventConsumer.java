package com.emailMarketing.streaming;

import org.springframework.stereotype.Component; import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.kafka.core.KafkaTemplate; import java.sql.Timestamp; import java.time.*; import org.slf4j.Logger; import org.slf4j.LoggerFactory; import io.github.resilience4j.retry.annotation.Retry;

@Component
@org.springframework.context.annotation.Profile("kafka")
public class EmailEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(EmailEventConsumer.class);
    private final JdbcTemplate jdbc; private final KafkaTemplate<String,Object> kafka;
    public EmailEventConsumer(JdbcTemplate jdbc, KafkaTemplate<String,Object> kafka){ this.jdbc=jdbc; this.kafka=kafka; }

    @KafkaListener(topics=KafkaTopics.EMAIL_EVENTS, groupId="analytics-email-events", containerFactory="emailEventListenerFactory")
    @Retry(name="dbInsertTransient")
    public void onMessage(EmailEventPayload payload){
        try {
            if(payload==null || payload.getEventId()==null) return;
            // Simple idempotent insert: swallow duplicate PK (ORA-00001)
            try {
                jdbc.update("INSERT INTO RAW_EMAIL_EVENTS(EVENT_ID,USER_ID,CAMPAIGN_ID,EVENT_TYPE,EVENT_TS,METADATA_JSON) VALUES (?,?,?,?,?,?)",
                    payload.getEventId(), payload.getUserId(), payload.getCampaignId(), payload.getType(), Timestamp.from(payload.getOccurredAt()==null? Instant.now(): payload.getOccurredAt()), payload.getMetadataJson());
            } catch(Exception inner){
                String msg = inner.getMessage();
                if(msg==null || !msg.contains("ORA-00001")) throw inner; // rethrow non-duplicate
                log.debug("duplicate_event_ignored eventId={}", payload.getEventId());
            }
        }catch(Exception ex){
            log.error("email_event_consume_error eventId={} err={}", payload==null?"null":payload.getEventId(), ex.getMessage());
            try { if(payload!=null) kafka.send(KafkaTopics.DLQ_EVENTS, payload.getEventId(), payload); } catch(Exception ignored){ }
        }
    }
}
