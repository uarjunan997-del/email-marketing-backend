package com.emailMarketing.streaming;

import org.apache.kafka.clients.producer.ProducerConfig; import org.apache.kafka.clients.consumer.ConsumerConfig; import org.apache.kafka.common.serialization.StringSerializer; import org.apache.kafka.common.serialization.StringDeserializer; import org.springframework.kafka.support.serializer.JsonSerializer; import org.springframework.kafka.support.serializer.JsonDeserializer; import org.springframework.context.annotation.*; import org.springframework.kafka.core.*; import org.springframework.beans.factory.annotation.Value; import java.util.*; import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory; import org.springframework.context.annotation.Profile;

@Configuration
@Profile("kafka")
public class KafkaConfig {
    @Value("${kafka.bootstrap-servers:localhost:9092}") private String bootstrap;

    @Bean
    public Map<String,Object> producerProps(){
        Map<String,Object> p = new HashMap<>();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        p.put(ProducerConfig.ACKS_CONFIG, "all");
        p.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        p.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return p;
    }
    @Bean public ProducerFactory<String,Object> producerFactory(){ return new DefaultKafkaProducerFactory<>(producerProps()); }
    @Bean public KafkaTemplate<String,Object> kafkaTemplate(){ return new KafkaTemplate<>(producerFactory()); }

    @Bean
    public Map<String,Object> consumerProps(){
        Map<String,Object> c = new HashMap<>();
        c.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        c.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        c.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        c.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        c.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        c.put(ConsumerConfig.GROUP_ID_CONFIG, "analytics-consumers");
        return c;
    }
    @Bean public ConsumerFactory<String,EmailEventPayload> emailEventConsumerFactory(){
        JsonDeserializer<EmailEventPayload> jd = new JsonDeserializer<>(EmailEventPayload.class); jd.addTrustedPackages("*");
        return new DefaultKafkaConsumerFactory<>(consumerProps(), new StringDeserializer(), jd);
    }
    @Bean public ConcurrentKafkaListenerContainerFactory<String,EmailEventPayload> emailEventListenerFactory(){
        ConcurrentKafkaListenerContainerFactory<String,EmailEventPayload> f = new ConcurrentKafkaListenerContainerFactory<>();
        f.setConsumerFactory(emailEventConsumerFactory()); f.setConcurrency(2); return f;
    }
}
