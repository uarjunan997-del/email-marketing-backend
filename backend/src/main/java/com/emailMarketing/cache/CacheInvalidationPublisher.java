package com.emailMarketing.cache;

import org.springframework.stereotype.Service; import org.springframework.kafka.core.KafkaTemplate; import com.emailMarketing.streaming.KafkaTopics; import java.util.Map; import org.slf4j.Logger; import org.slf4j.LoggerFactory;

@Service
public class CacheInvalidationPublisher {
    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationPublisher.class);
    private final KafkaTemplate<String,Object> kafka;
    public CacheInvalidationPublisher(KafkaTemplate<String,Object> kafka){ this.kafka=kafka; }
    public void invalidateCampaign(Long campaignId){ publish("campaign", campaignId); }
    public void invalidateBenchmarks(String industry){ publish("benchmarks", industry); }
    private void publish(String type, Object id){ try { kafka.send(KafkaTopics.CACHE_INVALIDATE, type+":"+id, Map.of("type",type,"id",id)); } catch(Exception ex){ log.error("cache_invalidation_publish_error type={} id={} msg={}", type, id, ex.getMessage()); } }
}