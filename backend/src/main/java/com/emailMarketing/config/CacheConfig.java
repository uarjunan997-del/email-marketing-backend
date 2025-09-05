package com.emailMarketing.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.CompositeCacheManager;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import java.util.Map;
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
      "dashboard_overview","dashboard_recent","dashboard_trends","dashboard_top",
      "ts_hourly","ts_daily","ts_weekly","ts_monthly",
  "ts_forecast","ts_anomalies"
  ,"ts_campaign_decomp","ts_campaign_trend","ts_campaign_anomalies"
    );
    mgr.setCaffeine(Caffeine.newBuilder()
      .expireAfterWrite(Duration.ofMinutes(5))
      .maximumSize(2000));
    return mgr;
  }

  @Bean(name="redisCacheManager")
  public CacheManager redisCacheManager(RedisConnectionFactory cf){
    RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(10));
    return RedisCacheManager.builder(cf).cacheDefaults(base).withInitialCacheConfigurations(Map.of(
      "dashboard_overview", base.entryTtl(Duration.ofMinutes(1)),
      "ts_campaign", base.entryTtl(Duration.ofMinutes(5))
    )).build();
  }

  @Bean
  @Primary
  public CacheManager resilientCacheManager(CacheManager cacheManager, @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection") RedisConnectionFactory cf){
    CacheManager redis;
    try {
      redis = redisCacheManager(cf);
    } catch (Exception e){
      redis = null; // fallback only
    }
    CompositeCacheManager composite = new CompositeCacheManager();
    if(redis!=null) composite.setCacheManagers(java.util.List.of(redis, cacheManager)); else composite.setCacheManagers(java.util.List.of(cacheManager));
    composite.setFallbackToNoOpCache(false);
    return composite;
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