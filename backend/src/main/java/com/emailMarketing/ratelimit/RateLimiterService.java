package com.emailMarketing.ratelimit;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimiterService {
    private static class Bucket { long tokens; Instant refillAt; }
    private final ConcurrentHashMap<String,Bucket> buckets = new ConcurrentHashMap<>();

    private final long capacity;
    private final long refillSeconds;

    public RateLimiterService(
        @Value("${rateLimiter.adminBackfill.capacity:5}") long capacity,
        @Value("${rateLimiter.adminBackfill.refillSeconds:300}") long refillSeconds){
        this.capacity=capacity; this.refillSeconds=refillSeconds; }

    public synchronized boolean allow(String key){
        Bucket b = buckets.computeIfAbsent(key,k->{ Bucket nb=new Bucket(); nb.tokens=capacity; nb.refillAt=Instant.now().plusSeconds(refillSeconds); return nb; });
        Instant now = Instant.now();
        if(now.isAfter(b.refillAt)){
            b.tokens = capacity; b.refillAt = now.plusSeconds(refillSeconds);
        }
        if(b.tokens>0){ b.tokens--; return true; }
        return false;
    }
    public long remaining(String key){ Bucket b = buckets.get(key); return b==null?capacity:b.tokens; }
}