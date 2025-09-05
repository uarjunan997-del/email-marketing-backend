package com.emailMarketing.timeseries;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Service
public class AggregationService {
    private final JdbcTemplate jdbc;
    public AggregationService(JdbcTemplate jdbc){ this.jdbc=jdbc; }

    // Incremental hourly aggregation for last 48 hours
    @Scheduled(cron="0 5 * * * *")
    @Transactional
    public void aggregateHourly(){
        LocalDateTime now = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
        LocalDateTime from = now.minusHours(47);
        // Delete overlapping buckets (idempotent rebuild window)
        jdbc.update("DELETE FROM email_metrics_hourly WHERE bucket_start BETWEEN ? AND ?", java.sql.Timestamp.valueOf(from), java.sql.Timestamp.valueOf(now));
        jdbc.update("INSERT /*+ APPEND */ INTO email_metrics_hourly (user_id,campaign_id,bucket_start,sent_count,open_count,click_count,bounce_count,order_count,revenue_amount) " +
            "SELECT e.user_id, e.campaign_id, TRUNC(e.event_time,'HH24') bucket_start, " +
            " SUM(CASE WHEN e.event_type='SENT' THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN e.event_type='OPEN' THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN e.event_type='CLICK' THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN e.event_type='BOUNCE' THEN 1 ELSE 0 END)," +
            " COALESCE(SUM(ra.order_ct),0) order_count, COALESCE(SUM(ra.rev),0) revenue_amount " +
            " FROM email_events e LEFT JOIN (" +
            "   SELECT user_id, campaign_id, TRUNC(created_at,'HH24') bucket_start, COUNT(DISTINCT order_id) order_ct, SUM(revenue_amount) rev" +
            "   FROM revenue_attribution WHERE created_at BETWEEN ? AND ? GROUP BY user_id,campaign_id,TRUNC(created_at,'HH24')" +
            ") ra ON ra.user_id=e.user_id AND ra.campaign_id=e.campaign_id AND ra.bucket_start=TRUNC(e.event_time,'HH24')" +
            " WHERE e.event_time BETWEEN ? AND ? GROUP BY e.user_id, e.campaign_id, TRUNC(e.event_time,'HH24')",
            java.sql.Timestamp.valueOf(from), java.sql.Timestamp.valueOf(now));
    }

    // Nightly daily/weekly/monthly rollup
    @Scheduled(cron="0 20 1 * * *")
    @Transactional
    public void aggregateDailyWeeklyMonthly(){
        LocalDate today = LocalDate.now();
        LocalDate fromDay = today.minusDays(30); // rebuild last 30 days daily for accuracy
        jdbc.update("DELETE FROM email_metrics_daily WHERE bucket_start >= ?", java.sql.Timestamp.valueOf(fromDay.atStartOfDay()));
        jdbc.update("INSERT /*+ APPEND */ INTO email_metrics_daily (user_id,campaign_id,bucket_start,sent_count,open_count,click_count,bounce_count,order_count,revenue_amount) " +
            "SELECT e.user_id, e.campaign_id, TRUNC(e.event_time) bucket_start, " +
            " SUM(CASE WHEN e.event_type='SENT' THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN e.event_type='OPEN' THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN e.event_type='CLICK' THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN e.event_type='BOUNCE' THEN 1 ELSE 0 END)," +
            " COALESCE(SUM(ra.order_ct),0), COALESCE(SUM(ra.rev),0) FROM email_events e LEFT JOIN (" +
            "   SELECT user_id, campaign_id, TRUNC(created_at) bucket_start, COUNT(DISTINCT order_id) order_ct, SUM(revenue_amount) rev" +
            "   FROM revenue_attribution WHERE created_at >= ? GROUP BY user_id,campaign_id,TRUNC(created_at)" +
            ") ra ON ra.user_id=e.user_id AND ra.campaign_id=e.campaign_id AND ra.bucket_start=TRUNC(e.event_time) " +
            " WHERE e.event_time >= ? GROUP BY e.user_id,e.campaign_id,TRUNC(e.event_time)",
            java.sql.Timestamp.valueOf(fromDay.atStartOfDay()));

        // Weekly (ISO Monday anchor) last 26 weeks
        LocalDate fromWeek = today.minusWeeks(26); LocalDate weekStart = fromWeek.minusDays((fromWeek.getDayOfWeek().getValue()+6)%7);
        jdbc.update("DELETE FROM email_metrics_weekly WHERE bucket_start >= ?", java.sql.Timestamp.valueOf(weekStart.atStartOfDay()));
        jdbc.update("INSERT /*+ APPEND */ INTO email_metrics_weekly (user_id,campaign_id,bucket_start,sent_count,open_count,click_count,bounce_count,order_count,revenue_amount) " +
            "SELECT e.user_id, e.campaign_id, TRUNC(e.event_time,'IW') bucket_start, " +
            " SUM(CASE WHEN e.event_type='SENT' THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN e.event_type='OPEN' THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN e.event_type='CLICK' THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN e.event_type='BOUNCE' THEN 1 ELSE 0 END)," +
            " COALESCE(SUM(ra.order_ct),0), COALESCE(SUM(ra.rev),0) FROM email_events e LEFT JOIN (" +
            "   SELECT user_id, campaign_id, TRUNC(created_at,'IW') bucket_start, COUNT(DISTINCT order_id) order_ct, SUM(revenue_amount) rev" +
            "   FROM revenue_attribution WHERE created_at >= ? GROUP BY user_id,campaign_id,TRUNC(created_at,'IW')" +
            ") ra ON ra.user_id=e.user_id AND ra.campaign_id=e.campaign_id AND ra.bucket_start=TRUNC(e.event_time,'IW') " +
            " WHERE e.event_time >= ? GROUP BY e.user_id,e.campaign_id,TRUNC(e.event_time,'IW')",
            java.sql.Timestamp.valueOf(weekStart.atStartOfDay()));

        // Monthly last 18 months
        LocalDate fromMonth = today.withDayOfMonth(1).minusMonths(17);
        jdbc.update("DELETE FROM email_metrics_monthly WHERE bucket_start >= ?", java.sql.Timestamp.valueOf(fromMonth.atStartOfDay()));
        jdbc.update("INSERT /*+ APPEND */ INTO email_metrics_monthly (user_id,campaign_id,bucket_start,sent_count,open_count,click_count,bounce_count,order_count,revenue_amount) " +
            "SELECT e.user_id, e.campaign_id, TRUNC(e.event_time,'MM') bucket_start, " +
            " SUM(CASE WHEN e.event_type='SENT' THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN e.event_type='OPEN' THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN e.event_type='CLICK' THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN e.event_type='BOUNCE' THEN 1 ELSE 0 END)," +
            " COALESCE(SUM(ra.order_ct),0), COALESCE(SUM(ra.rev),0) FROM email_events e LEFT JOIN (" +
            "   SELECT user_id, campaign_id, TRUNC(created_at,'MM') bucket_start, COUNT(DISTINCT order_id) order_ct, SUM(revenue_amount) rev" +
            "   FROM revenue_attribution WHERE created_at >= ? GROUP BY user_id,campaign_id,TRUNC(created_at,'MM')" +
            ") ra ON ra.user_id=e.user_id AND ra.campaign_id=e.campaign_id AND ra.bucket_start=TRUNC(e.event_time,'MM') " +
            " WHERE e.event_time >= ? GROUP BY e.user_id,e.campaign_id,TRUNC(e.event_time,'MM')",
            java.sql.Timestamp.valueOf(fromMonth.atStartOfDay()));
    }

    // Manual trigger utility
    @Transactional
    public void backfillAll(int days){
        LocalDateTime from = LocalDateTime.now().minusDays(days);
        jdbc.update("DELETE FROM email_metrics_hourly WHERE bucket_start >= ?", java.sql.Timestamp.valueOf(from));
        jdbc.update("INSERT /*+ APPEND */ INTO email_metrics_hourly (user_id,campaign_id,bucket_start,sent_count,open_count,click_count,bounce_count,order_count,revenue_amount) " +
            "SELECT user_id, campaign_id, TRUNC(event_time,'HH24') bucket_start, " +
            " SUM(CASE WHEN event_type='SENT' THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN event_type='OPEN' THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN event_type='CLICK' THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN event_type='BOUNCE' THEN 1 ELSE 0 END)," +
            " 0,0 FROM email_events WHERE event_time >= ? GROUP BY user_id,campaign_id,TRUNC(event_time,'HH24')",
            java.sql.Timestamp.valueOf(from));
        aggregateDailyWeeklyMonthly();
    }

    // Backfill a specific user and time window (hourly + cascaded rollups) used by secured admin endpoint
    @Transactional
    public void manualBackfill(Long userId, java.time.Instant start, java.time.Instant end){
        LocalDateTime from = LocalDateTime.ofInstant(start, java.time.ZoneOffset.UTC).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime to = LocalDateTime.ofInstant(end, java.time.ZoneOffset.UTC).withMinute(0).withSecond(0).withNano(0);
        jdbc.update("DELETE FROM email_metrics_hourly WHERE user_id=? AND bucket_start BETWEEN ? AND ?", userId, java.sql.Timestamp.valueOf(from), java.sql.Timestamp.valueOf(to));
        jdbc.update("INSERT /*+ APPEND */ INTO email_metrics_hourly (user_id,campaign_id,bucket_start,sent_count,open_count,click_count,bounce_count,order_count,revenue_amount) " +
            "SELECT e.user_id, e.campaign_id, TRUNC(e.event_time,'HH24') bucket_start, " +
            " SUM(CASE WHEN e.event_type='SENT' THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN e.event_type='OPEN' THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN e.event_type='CLICK' THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN e.event_type='BOUNCE' THEN 1 ELSE 0 END)," +
            " COALESCE(SUM(ra.order_ct),0), COALESCE(SUM(ra.rev),0) FROM email_events e LEFT JOIN (" +
            "   SELECT user_id, campaign_id, TRUNC(created_at,'HH24') bucket_start, COUNT(DISTINCT order_id) order_ct, SUM(revenue_amount) rev" +
            "   FROM revenue_attribution WHERE user_id=? AND created_at BETWEEN ? AND ? GROUP BY user_id,campaign_id,TRUNC(created_at,'HH24')" +
            ") ra ON ra.user_id=e.user_id AND ra.campaign_id=e.campaign_id AND ra.bucket_start=TRUNC(e.event_time,'HH24') " +
            " WHERE e.user_id=? AND e.event_time BETWEEN ? AND ? GROUP BY e.user_id,e.campaign_id,TRUNC(e.event_time,'HH24')",
            userId, java.sql.Timestamp.valueOf(from), java.sql.Timestamp.valueOf(to), userId, java.sql.Timestamp.valueOf(from), java.sql.Timestamp.valueOf(to));
        // Rebuild daily buckets overlapping window (extend a day each side for safety)
        LocalDate fromDay = from.minusDays(1).toLocalDate(); LocalDate toDay = to.plusDays(1).toLocalDate();
        jdbc.update("DELETE FROM email_metrics_daily WHERE user_id=? AND bucket_start BETWEEN ? AND ?", userId, java.sql.Timestamp.valueOf(fromDay.atStartOfDay()), java.sql.Timestamp.valueOf(toDay.atStartOfDay()));
        jdbc.update("INSERT /*+ APPEND */ INTO email_metrics_daily (user_id,campaign_id,bucket_start,sent_count,open_count,click_count,bounce_count,order_count,revenue_amount) " +
            "SELECT e.user_id,campaign_id, TRUNC(e.event_time) bucket_start, " +
            " SUM(CASE WHEN e.event_type='SENT' THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN e.event_type='OPEN' THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN e.event_type='CLICK' THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN e.event_type='BOUNCE' THEN 1 ELSE 0 END)," +
            " COALESCE(SUM(ra.order_ct),0), COALESCE(SUM(ra.rev),0) FROM email_events e LEFT JOIN (" +
            "   SELECT user_id, campaign_id, TRUNC(created_at) bucket_start, COUNT(DISTINCT order_id) order_ct, SUM(revenue_amount) rev" +
            "   FROM revenue_attribution WHERE user_id=? AND created_at BETWEEN ? AND ? GROUP BY user_id,campaign_id,TRUNC(created_at)" +
            ") ra ON ra.user_id=e.user_id AND ra.campaign_id=e.campaign_id AND ra.bucket_start=TRUNC(e.event_time) " +
            " WHERE e.user_id=? AND e.event_time BETWEEN ? AND ? GROUP BY e.user_id,e.campaign_id,TRUNC(e.event_time)",
            userId, java.sql.Timestamp.valueOf(fromDay.atStartOfDay()), java.sql.Timestamp.valueOf(toDay.atStartOfDay()), userId, java.sql.Timestamp.valueOf(fromDay.atStartOfDay()), java.sql.Timestamp.valueOf(toDay.atStartOfDay()));
        // Weekly (ISO Monday)
        jdbc.update("DELETE FROM email_metrics_weekly WHERE user_id=? AND bucket_start BETWEEN ? AND ?", userId, java.sql.Timestamp.valueOf(fromDay.atStartOfDay()), java.sql.Timestamp.valueOf(toDay.atStartOfDay()));
        jdbc.update("INSERT /*+ APPEND */ INTO email_metrics_weekly (user_id,campaign_id,bucket_start,sent_count,open_count,click_count,bounce_count,order_count,revenue_amount) " +
            "SELECT e.user_id,campaign_id, TRUNC(e.event_time,'IW') bucket_start, " +
            " SUM(CASE WHEN e.event_type='SENT' THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN e.event_type='OPEN' THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN e.event_type='CLICK' THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN e.event_type='BOUNCE' THEN 1 ELSE 0 END)," +
            " COALESCE(SUM(ra.order_ct),0), COALESCE(SUM(ra.rev),0) FROM email_events e LEFT JOIN (" +
            "   SELECT user_id, campaign_id, TRUNC(created_at,'IW') bucket_start, COUNT(DISTINCT order_id) order_ct, SUM(revenue_amount) rev" +
            "   FROM revenue_attribution WHERE user_id=? AND created_at BETWEEN ? AND ? GROUP BY user_id,campaign_id,TRUNC(created_at,'IW')" +
            ") ra ON ra.user_id=e.user_id AND ra.campaign_id=e.campaign_id AND ra.bucket_start=TRUNC(e.event_time,'IW') " +
            " WHERE e.user_id=? AND e.event_time BETWEEN ? AND ? GROUP BY e.user_id,e.campaign_id,TRUNC(e.event_time,'IW')",
            userId, java.sql.Timestamp.valueOf(fromDay.atStartOfDay()), java.sql.Timestamp.valueOf(toDay.atStartOfDay()), userId, java.sql.Timestamp.valueOf(fromDay.atStartOfDay()), java.sql.Timestamp.valueOf(toDay.atStartOfDay()));
        // Monthly
        jdbc.update("DELETE FROM email_metrics_monthly WHERE user_id=? AND bucket_start BETWEEN ? AND ?", userId, java.sql.Timestamp.valueOf(fromDay.atStartOfDay()), java.sql.Timestamp.valueOf(toDay.atStartOfDay()));
        jdbc.update("INSERT /*+ APPEND */ INTO email_metrics_monthly (user_id,campaign_id,bucket_start,sent_count,open_count,click_count,bounce_count,order_count,revenue_amount) " +
            "SELECT e.user_id,campaign_id, TRUNC(e.event_time,'MM') bucket_start, " +
            " SUM(CASE WHEN e.event_type='SENT' THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN e.event_type='OPEN' THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN e.event_type='CLICK' THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN e.event_type='BOUNCE' THEN 1 ELSE 0 END)," +
            " COALESCE(SUM(ra.order_ct),0), COALESCE(SUM(ra.rev),0) FROM email_events e LEFT JOIN (" +
            "   SELECT user_id, campaign_id, TRUNC(created_at,'MM') bucket_start, COUNT(DISTINCT order_id) order_ct, SUM(revenue_amount) rev" +
            "   FROM revenue_attribution WHERE user_id=? AND created_at BETWEEN ? AND ? GROUP BY user_id,campaign_id,TRUNC(created_at,'MM')" +
            ") ra ON ra.user_id=e.user_id AND ra.campaign_id=e.campaign_id AND ra.bucket_start=TRUNC(e.event_time,'MM') " +
            " WHERE e.user_id=? AND e.event_time BETWEEN ? AND ? GROUP BY e.user_id,e.campaign_id,TRUNC(e.event_time,'MM')",
            userId, java.sql.Timestamp.valueOf(fromDay.atStartOfDay()), java.sql.Timestamp.valueOf(toDay.atStartOfDay()), userId, java.sql.Timestamp.valueOf(fromDay.atStartOfDay()), java.sql.Timestamp.valueOf(toDay.atStartOfDay()));
    }
}
