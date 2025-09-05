package com.emailMarketing.timeseries;

import com.emailMarketing.timeseries.repo.AdminBackfillAuditRepository;
import com.emailMarketing.subscription.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class TimeSeriesAdminUnitTest {
    TimeSeriesAnalyticsService tsService = mock(TimeSeriesAnalyticsService.class);
    UserRepository userRepo = mock(UserRepository.class);
    AggregationService aggregationService = mock(AggregationService.class);
    com.emailMarketing.ratelimit.RateLimiterService rateLimiter = mock(com.emailMarketing.ratelimit.RateLimiterService.class);
    org.springframework.jdbc.core.JdbcTemplate jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
    AdminBackfillAuditRepository auditRepo = mock(AdminBackfillAuditRepository.class);
    TimeSeriesAnalyticsController controller;

    @BeforeEach void init(){
        controller = new TimeSeriesAnalyticsController(tsService, userRepo, aggregationService, rateLimiter, jdbc, auditRepo);
    }

    @Test void backfillRejectsBadRange(){
        var resp = controller.triggerBackfill(fakeAdmin(), 1L, Instant.now(), Instant.now().minusSeconds(60));
        assertEquals(400, resp.getStatusCode().value());
    }

    @Test void backfillCreatesAuditAndCallsAggregation(){
        when(rateLimiter.allow(anyString())).thenReturn(true);
        AdminBackfillAudit saved = new AdminBackfillAudit();
        // emulate JPA assigning id
        saved.setStatus("STARTED");
        try { java.lang.reflect.Field f=AdminBackfillAudit.class.getDeclaredField("id"); f.setAccessible(true); f.set(saved, 5L);} catch(Exception ignore){}
        when(auditRepo.save(any())).thenReturn(saved);
        Instant start = Instant.now().minusSeconds(3600);
        Instant end = Instant.now();
        ResponseEntity<String> resp = controller.triggerBackfill(fakeAdmin(), 10L, start, end);
        assertEquals(200, resp.getStatusCode().value());
        verify(auditRepo, times(1)).save(any());
        verify(aggregationService, times(1)).manualBackfill(eq(10L), eq(start), eq(end));
    }

    @Test void rateLimitBlocks(){
        when(rateLimiter.allow(anyString())).thenReturn(false);
        Instant start = Instant.now().minusSeconds(3600), end = Instant.now();
        var resp = controller.triggerBackfill(fakeAdmin(), 10L, start, end);
        assertEquals(429, resp.getStatusCode().value());
        verify(aggregationService, never()).manualBackfill(any(), any(), any());
    }

    private org.springframework.security.core.userdetails.User fakeAdmin(){
        return new org.springframework.security.core.userdetails.User("admin","x", java.util.List.of(()->"ROLE_ADMIN"));
    }
}