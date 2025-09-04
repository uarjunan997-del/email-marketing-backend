package com.emailMarketing.dashboard.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class DashboardDtos {
  public record Overview(long totalContacts, long activeContacts, long unsubscribedContacts,
                          long campaignsLast30, Map<String, Long> statusBreakdown,
                          double deliveryRate, double openRate, double clickRate,
                          double reputationScore) {}
  public record RecentCampaign(Long id, String name, String status, int sent, int opens, int clicks, double openRate, double clickRate, LocalDateTime createdAt){}
  public record TrendsPoint(LocalDateTime day, long sent, long opens, long clicks){}
  public record Usage(long monthlyLimit, long usedThisMonth, long remaining){}
  public record TopCampaign(Long id, String name, double engagementScore, int opens, int clicks){}
}
