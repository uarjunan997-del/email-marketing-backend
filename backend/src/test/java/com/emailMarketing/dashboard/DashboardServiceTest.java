package com.emailMarketing.dashboard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import com.emailMarketing.analytics.DailyEmailEventsRepository;
import com.emailMarketing.analytics.EmailEventRepository;
import com.emailMarketing.campaign.Campaign;
import com.emailMarketing.campaign.CampaignRepository;
import com.emailMarketing.contact.ContactRepository;
import com.emailMarketing.subscription.UserRepository;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DashboardServiceTest {
  private ContactRepository contactRepo; private CampaignRepository campaignRepo; private EmailEventRepository eventRepo; private DailyEmailEventsRepository dailyRepo; private UserRepository userRepository; private DashboardService service;

  @BeforeEach
  void setup(){
    contactRepo = Mockito.mock(ContactRepository.class);
    campaignRepo = Mockito.mock(CampaignRepository.class);
    eventRepo = Mockito.mock(EmailEventRepository.class);
    dailyRepo = Mockito.mock(DailyEmailEventsRepository.class);
  userRepository = Mockito.mock(UserRepository.class);
  service = new DashboardService(contactRepo, campaignRepo, eventRepo, dailyRepo, new ConcurrentMapCacheManager("dashboard_overview","dashboard_recent","dashboard_trends","dashboard_top"), userRepository);
  }

  @Test
  void overview_basicRates(){
    Mockito.when(contactRepo.countByUserId(1L)).thenReturn(100L);
    Mockito.when(contactRepo.countByUserIdAndUnsubscribedTrue(1L)).thenReturn(5L);
    Campaign c = new Campaign(); c.setUserId(1L); c.setName("C1"); c.setStatus("COMPLETED"); c.setCreatedAt(LocalDateTime.now().minusDays(1));
    Mockito.when(campaignRepo.findByUserId(1L)).thenReturn(List.of(c));
    Mockito.when(eventRepo.aggregateEventsSince(Mockito.eq(1L), Mockito.any())).thenReturn(List.of(
      new Object[]{"SENT", 200L}, new Object[]{"OPEN", 50L}, new Object[]{"CLICK", 10L}
    ));
    var o = service.overview(1L);
    assertEquals(100, o.totalContacts());
    assertTrue(o.openRate() > 0);
  }

  @Test
  void trends_fallback(){
    Mockito.when(dailyRepo.aggregateDaily(Mockito.eq(1L), Mockito.any())).thenThrow(new RuntimeException("MV missing"));
    Mockito.when(eventRepo.dailyBreakdown(Mockito.eq(1L), Mockito.any())).thenReturn(List.of(
      new Object[]{java.sql.Timestamp.valueOf(LocalDateTime.now().minusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0)), "SENT", 5L},
      new Object[]{java.sql.Timestamp.valueOf(LocalDateTime.now().minusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0)), "OPEN", 2L}
    ));
    var t = service.trends(1L, "30d");
    assertFalse(t.isEmpty());
  }
}
