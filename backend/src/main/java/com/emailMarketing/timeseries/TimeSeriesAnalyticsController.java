package com.emailMarketing.timeseries;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.Instant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.jdbc.core.JdbcTemplate;
import com.emailMarketing.ratelimit.RateLimiterService;
import com.emailMarketing.timeseries.repo.AdminBackfillAuditRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import com.emailMarketing.subscription.UserRepository;
import java.util.*;

@RestController
@RequestMapping("/api/timeseries")
public class TimeSeriesAnalyticsController {
    private final TimeSeriesAnalyticsService service; private final UserRepository userRepo;
    private final AggregationService aggregationService; private final RateLimiterService rateLimiter; private final JdbcTemplate jdbc; private final AdminBackfillAuditRepository auditRepo;
    public TimeSeriesAnalyticsController(TimeSeriesAnalyticsService service, UserRepository userRepo, AggregationService aggregationService, RateLimiterService rateLimiter, JdbcTemplate jdbc, AdminBackfillAuditRepository auditRepo){ this.service=service; this.userRepo=userRepo; this.aggregationService=aggregationService; this.rateLimiter=rateLimiter; this.jdbc=jdbc; this.auditRepo=auditRepo; }
    private Long uid(UserDetails p){ return userRepo.findByUsername(p.getUsername()).map(u->u.getId()).orElseThrow(); }

    @GetMapping("/daily")
    public TimeSeriesAnalyticsService.TrendResponse daily(@AuthenticationPrincipal UserDetails p, @RequestParam(defaultValue="30") int days){
        if(days>730) days=730; // 2 year cap
        return service.daily(uid(p), days);
    }

    @GetMapping("/hourly")
    public List<TimeSeriesAnalyticsService.Point> hourly(@AuthenticationPrincipal UserDetails p, @RequestParam(defaultValue="48") int hours){
        if(hours>720) hours=720; // 30 days of hours cap
        return service.hourly(uid(p), hours);
    }

    @GetMapping("/weekly")
    public TimeSeriesAnalyticsService.TrendResponse weekly(@AuthenticationPrincipal UserDetails p, @RequestParam(defaultValue="12") int weeks){
        if(weeks>104) weeks=104; // 2 years
        return service.weekly(uid(p), weeks);
    }

    @GetMapping("/monthly")
    public TimeSeriesAnalyticsService.TrendResponse monthly(@AuthenticationPrincipal UserDetails p, @RequestParam(defaultValue="12") int months){
        if(months>36) months=36; // 3 year cap for safety
        return service.monthly(uid(p), months);
    }

    @GetMapping("/moving-averages")
    public java.util.Map<String,Object> moving(@AuthenticationPrincipal UserDetails p, @RequestParam(defaultValue="90") int days){
        if(days>365) days=365;
        return service.movingAverages(uid(p), days);
    }

    @GetMapping("/seasonality/weekday")
    public java.util.Map<String,Object> weekdaySeasonality(@AuthenticationPrincipal UserDetails p, @RequestParam(defaultValue="8") int weeks){
        if(weeks>52) weeks=52;
        return service.weekdaySeasonality(uid(p), weeks);
    }

    @GetMapping("/yoy/open-rate")
    public java.util.Map<String,Object> yoy(@AuthenticationPrincipal UserDetails p){
        return service.yearOverYear(uid(p));
    }

    @GetMapping("/forecast")
    public java.util.Map<String,Object> forecast(@AuthenticationPrincipal UserDetails p, @RequestParam(defaultValue="14") int horizon){
        return service.forecast(uid(p), horizon);
    }

    // Renamed to avoid collision with campaign-level /api/timeseries/anomalies endpoint
    @GetMapping("/anomalies/aggregate")
    public java.util.Map<String,Object> anomalies(@AuthenticationPrincipal UserDetails p, @RequestParam(defaultValue="60") int days){
        if(days>180) days=180; return service.anomalies(uid(p), days);
    }

    @GetMapping("/forecast/backtest")
    public java.util.Map<String,Object> backtest(@AuthenticationPrincipal UserDetails p, @RequestParam(defaultValue="90") int trainDays, @RequestParam(defaultValue="14") int testDays){
        return service.backtest(uid(p), trainDays, testDays);
    }

    @PostMapping("/admin/aggregate/backfill")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> triggerBackfill(@AuthenticationPrincipal UserDetails admin,
                                                  @RequestParam Long userId,
                                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
                                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end) {
        if(userId==null || userId<=0) return ResponseEntity.badRequest().body("Invalid userId");
        if(start==null || end==null || start.isAfter(end)) return ResponseEntity.badRequest().body("Invalid time range");
        if(start.isBefore(Instant.now().minusSeconds(400L*24*3600))){ start = Instant.now().minusSeconds(400L*24*3600); }
        String key = "admin:backfill:"+admin.getUsername();
        if(!rateLimiter.allow(key)){
            return ResponseEntity.status(429).body("Rate limit exceeded. Remaining="+rateLimiter.remaining(key));
        }
        long hours = (end.toEpochMilli()-start.toEpochMilli())/3600000L;
    com.emailMarketing.timeseries.AdminBackfillAudit record = new com.emailMarketing.timeseries.AdminBackfillAudit();
    record.setAdminUsername(admin.getUsername()); record.setUserId(userId); record.setStartInstant(start); record.setEndInstant(end); record.setWindowHours(hours); record.setStatus("STARTED");
    record = auditRepo.save(record);
    Long auditId = record.getId();
        try{
            aggregationService.manualBackfill(userId, start, end);
            jdbc.update("UPDATE admin_backfill_audit SET status='DONE' WHERE id=?", auditId);
        }catch(Exception ex){
            jdbc.update("UPDATE admin_backfill_audit SET status='ERROR', message=? WHERE id=?", ex.getMessage(), auditId);
            return ResponseEntity.internalServerError().body("Backfill failed");
        }
        return ResponseEntity.ok("Backfill started");
    }
    @GetMapping("/admin/aggregate/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public java.util.List<com.emailMarketing.timeseries.AdminBackfillAudit> recentAudits(){
        return auditRepo.findTop50ByOrderByTriggeredAtDesc();
    }
}
