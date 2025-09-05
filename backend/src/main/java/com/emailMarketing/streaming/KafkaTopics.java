package com.emailMarketing.streaming;

public interface KafkaTopics {
    String EMAIL_EVENTS = "email_events";
    String ECOMMERCE_ORDERS = "ecommerce_orders";
    String CACHE_INVALIDATE = "cache_invalidate";
    String BENCHMARK_UPDATES = "benchmark_updates";
    String DLQ_EVENTS = "events_dlq";
}
