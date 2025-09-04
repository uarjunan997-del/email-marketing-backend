package com.emailMarketing.security;

import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryRateLimiter {
  private record Bucket(int attempts, Instant windowStart){}
  private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
  private final int limit = 5; // attempts
  private final Duration window = Duration.ofMinutes(1);

  public synchronized boolean isAllowed(String key){
    var now = Instant.now();
    var b = buckets.get(key);
    if(b == null || now.isAfter(b.windowStart.plus(window))){
      buckets.put(key, new Bucket(1, now));
      return true;
    }
    if(b.attempts + 1 > limit){
      return false;
    }
    buckets.put(key, new Bucket(b.attempts + 1, b.windowStart));
    return true;
  }

  public synchronized void reset(String key){ buckets.remove(key); }
}
