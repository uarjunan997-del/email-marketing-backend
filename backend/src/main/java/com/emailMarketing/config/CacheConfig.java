package com.emailMarketing.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.time.Duration;

@Configuration
@EnableCaching
@EnableAsync
@EnableScheduling
public class CacheConfig {

  @Bean
  public CacheManager cacheManager(){
    CaffeineCacheManager mgr = new CaffeineCacheManager(
      "dashboard_overview","dashboard_recent","dashboard_trends","dashboard_top"
    );
    mgr.setCaffeine(Caffeine.newBuilder()
      .expireAfterWrite(Duration.ofMinutes(5))
      .maximumSize(1000));
    return mgr;
  }

  @Bean
  public TaskExecutor analyticsExecutor(){
    ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
    ex.setThreadNamePrefix("analytics-");
    ex.setCorePoolSize(4); ex.setMaxPoolSize(8); ex.setQueueCapacity(100);
    ex.initialize();
    return ex;
  }
}