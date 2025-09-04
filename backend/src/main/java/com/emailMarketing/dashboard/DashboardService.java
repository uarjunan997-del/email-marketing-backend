package com.emailMarketing.dashboard;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.emailMarketing.analytics.DailyEmailEventsRepository;
import com.emailMarketing.analytics.EmailEventRepository;
import com.emailMarketing.campaign.Campaign;
import com.emailMarketing.campaign.CampaignRepository;
import com.emailMarketing.contact.ContactRepository;
import com.emailMarketing.dashboard.dto.DashboardDtos.*;
import com.emailMarketing.subscription.Subscription;
import com.emailMarketing.subscription.UserRepository;

import org.springframework.cache.annotation.Cacheable;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.cache.CacheManager;

@Service
public class DashboardService {
  private final ContactRepository contactRepo; private final CampaignRepository campaignRepo; private final EmailEventRepository eventRepo; private final DailyEmailEventsRepository dailyRepo; private final CacheManager cacheManager; private final UserRepository userRepository;
  public DashboardService(ContactRepository contactRepo, CampaignRepository campaignRepo, EmailEventRepository eventRepo, DailyEmailEventsRepository dailyRepo, CacheManager cacheManager, UserRepository userRepository){this.contactRepo=contactRepo; this.campaignRepo=campaignRepo; this.eventRepo=eventRepo; this.dailyRepo=dailyRepo; this.cacheManager=cacheManager; this.userRepository=userRepository;}

  @Cacheable("dashboard_overview")
  public Overview overview(Long userId){
    long total = contactRepo.countByUserId(userId);
    long unsub = contactRepo.countByUserIdAndUnsubscribedTrue(userId);
    long active = total - unsub;
    LocalDateTime since30 = LocalDateTime.now().minusDays(30);
    var campaigns = campaignRepo.findByUserId(userId);
    long last30 = campaigns.stream().filter(c->c.getCreatedAt().isAfter(since30)).count();
    Map<String, Long> statusBreakdown = new HashMap<>();
    campaigns.forEach(c-> statusBreakdown.merge(c.getStatus(),1L,Long::sum));

    // Aggregate email events for performance rates
    var events = eventRepo.aggregateEventsSince(userId, since30);
    long sent=0, opens=0, clicks=0, bounces=0;
    for(Object[] row: events){
      String type=(String)row[0]; long cnt=(Long)row[1];
      switch(type){
        case "SENT" -> sent+=cnt;
        case "OPEN" -> opens+=cnt;
        case "CLICK" -> clicks+=cnt;
        case "BOUNCE" -> bounces+=cnt;
      }
    }
    double deliveryRate = sent==0?0: (double)(sent-bounces)/sent;
    double openRate = sent==0?0: (double)opens/sent;
    double clickRate = sent==0?0: (double)clicks/sent;
    double reputation = Math.max(0, 100 - (bounces*2));
    return new Overview(total, active, unsub, last30, statusBreakdown, round(deliveryRate), round(openRate), round(clickRate), round(reputation));
  }

  @Cacheable("dashboard_recent")
  public List<RecentCampaign> recentCampaigns(Long userId){
    return campaignRepo.findByUserId(userId).stream()
      .sorted(Comparator.comparing(Campaign::getCreatedAt).reversed())
      .limit(10)
      .map(c-> new RecentCampaign(c.getId(), c.getName(), c.getStatus(), c.getSentCount(), c.getOpenCount(), c.getClickCount(), rate(c.getOpenCount(), c.getSentCount()), rate(c.getClickCount(), c.getSentCount()), c.getCreatedAt()))
      .toList();
  }

  @Cacheable("dashboard_trends")
  public List<TrendsPoint> trends(Long userId, String period){
    long days = switch(period){
      case "90d" -> 90; case "1y" -> 365; default -> 30; };
    LocalDateTime since = LocalDateTime.now().minusDays(days);
    // Use materialized view if available
    Map<LocalDateTime, long[]> m = new TreeMap<>();
    try {
      var mv = dailyRepo.aggregateDaily(userId, since.toLocalDate());
      for(Object[] row: mv){
        java.sql.Timestamp ts = (java.sql.Timestamp)row[0];
        LocalDateTime day = ts.toLocalDateTime().truncatedTo(ChronoUnit.DAYS);
        long sent = ((Number)row[1]).longValue();
        long open = ((Number)row[2]).longValue();
        long click = ((Number)row[3]).longValue();
        m.put(day, new long[]{sent, open, click});
      }
    } catch(Exception ex){
      // fallback to direct aggregation if MV not present
      var raw = eventRepo.dailyBreakdown(userId, since);
      for(Object[] r: raw){
        LocalDateTime day = ((java.sql.Timestamp)r[0]).toLocalDateTime().truncatedTo(ChronoUnit.DAYS);
        String type=(String)r[1]; long cnt=((Number)r[2]).longValue();
        var arr = m.computeIfAbsent(day, d-> new long[3]);
        switch(type){ case "SENT" -> arr[0]+=cnt; case "OPEN" -> arr[1]+=cnt; case "CLICK" -> arr[2]+=cnt; }
      }
    }
    List<TrendsPoint> out = new ArrayList<>();
    m.forEach((d,a)-> out.add(new TrendsPoint(d, a[0], a[1], a[2])));
    return out;
  }

  @Cacheable("dashboard_top")
  public List<TopCampaign> topCampaigns(Long userId){
    var data = eventRepo.campaignEngagement(userId);
    Map<Long, int[]> map = new HashMap<>();
    for(Object[] r: data){
      Long cid = (Long)r[0]; long opens = ((Number)r[1]).longValue(); long clicks = ((Number)r[2]).longValue();
      map.put(cid, new int[]{(int)opens,(int)clicks});
    }
    return campaignRepo.findByUserId(userId).stream()
      .map(c->{ var stats = map.getOrDefault(c.getId(), new int[]{0,0}); double engagement = (stats[0]*0.5 + stats[1]); return new TopCampaign(c.getId(), c.getName(), round(engagement), stats[0], stats[1]); })
      .sorted(Comparator.comparing(TopCampaign::engagementScore).reversed())
      .limit(10).toList();
  }

  public Usage usage(Long userId){
    // Resolve user subscription and map plan tier to usage limit (emails per month)
    var user = userRepository.findById(userId).orElse(null);
    Subscription.PlanType planType = user!=null && user.getSubscription()!=null ? user.getSubscription().getPlanType() : Subscription.PlanType.FREE;
    long monthlyLimit = switch(planType){
      case FREE -> 10000L; // baseline free tier
      case PRO -> 100000L;
      case PREMIUM -> 1000000L;
    };
    LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
    long sentThisMonth = eventRepo.aggregateEventsSince(userId, monthStart).stream().filter(r->"SENT".equals(r[0])).mapToLong(r->(Long)r[1]).sum();
    long remaining = Math.max(0, monthlyLimit - sentThisMonth);
    return new Usage(monthlyLimit, sentThisMonth, remaining);
  }

  public double rate(int part, int total){ return total==0?0: round((double)part/total); }
  private double round(double v){ return Math.round(v*10000.0)/100.0; }

  @Async("analyticsExecutor")
  public void refreshOverviewCacheAsync(Long userId){ overview(userId); }

  public void evictUserCaches(Long userId){
    for(String name: List.of("dashboard_overview","dashboard_recent","dashboard_trends","dashboard_top")){
      var c = cacheManager.getCache(name); if(c!=null) c.evict(userId);
    }
  }

  // Periodic trigger for MV refresh (requires DB job / call to refresh MV manually if FAST refresh not configured)
  @Scheduled(cron="0 10 * * * *")
  public void scheduledWarmCaches(){ /* iterate user IDs if multi-tenant - placeholder */ }
}
