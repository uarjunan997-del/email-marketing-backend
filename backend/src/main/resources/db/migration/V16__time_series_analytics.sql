-- Time-series analytics schema & performance objects
-- Hourly, daily, weekly, monthly aggregations + forecasting support
-- Assumes Oracle 19c+; adjust syntax for earlier versions if needed.

/*
 Overview:
 1. Base aggregate tables (partitioned) capturing rollups at multiple granularities.
 2. Materialized views for pre-computed rolling metrics & period comparisons.
 3. Index strategy optimized for (user_id, time_bucket) lookups.
 4. Helper forecasting staging table for seasonal decomposition.
 5. Partition management procedures (documented) for lifecycle.
*/

------------------------------------------------------------
-- 1. PARTITIONED AGGREGATE TABLES
------------------------------------------------------------
-- Using RANGE partition on bucket_start (bucket boundary). For Weekly we store ISO week Monday date.

DECLARE
  e_exists EXCEPTION; PRAGMA EXCEPTION_INIT(e_exists,-955);
BEGIN
  BEGIN EXECUTE IMMEDIATE 'CREATE TABLE email_metrics_hourly ( user_id NUMBER NOT NULL, campaign_id NUMBER, bucket_start DATE NOT NULL, sent_count NUMBER DEFAULT 0, open_count NUMBER DEFAULT 0, click_count NUMBER DEFAULT 0, bounce_count NUMBER DEFAULT 0, order_count NUMBER DEFAULT 0, revenue_amount NUMBER(18,4) DEFAULT 0, PRIMARY KEY(user_id, bucket_start, campaign_id) ) PARTITION BY RANGE (bucket_start) INTERVAL (NUMTODSINTERVAL(1,''DAY'')) ( PARTITION p0 VALUES LESS THAN (TO_DATE(''2025-01-01'',''YYYY-MM-DD'')) )'; EXCEPTION WHEN e_exists THEN NULL; END;
  BEGIN EXECUTE IMMEDIATE 'CREATE TABLE email_metrics_daily ( user_id NUMBER NOT NULL, campaign_id NUMBER, bucket_start DATE NOT NULL, sent_count NUMBER DEFAULT 0, open_count NUMBER DEFAULT 0, click_count NUMBER DEFAULT 0, bounce_count NUMBER DEFAULT 0, order_count NUMBER DEFAULT 0, revenue_amount NUMBER(18,4) DEFAULT 0, PRIMARY KEY(user_id, bucket_start, campaign_id) ) PARTITION BY RANGE (bucket_start) INTERVAL (NUMTOYMINTERVAL(1,''MONTH'')) ( PARTITION p0 VALUES LESS THAN (TO_DATE(''2025-01-01'',''YYYY-MM-DD'')) )'; EXCEPTION WHEN e_exists THEN NULL; END;
  BEGIN EXECUTE IMMEDIATE 'CREATE TABLE email_metrics_weekly ( user_id NUMBER NOT NULL, campaign_id NUMBER, bucket_start DATE NOT NULL, sent_count NUMBER DEFAULT 0, open_count NUMBER DEFAULT 0, click_count NUMBER DEFAULT 0, bounce_count NUMBER DEFAULT 0, order_count NUMBER DEFAULT 0, revenue_amount NUMBER(18,4) DEFAULT 0, PRIMARY KEY(user_id, bucket_start, campaign_id) ) PARTITION BY RANGE (bucket_start) INTERVAL (NUMTOYMINTERVAL(1,''MONTH'')) ( PARTITION p0 VALUES LESS THAN (TO_DATE(''2025-01-01'',''YYYY-MM-DD'')) )'; EXCEPTION WHEN e_exists THEN NULL; END;
  BEGIN EXECUTE IMMEDIATE 'CREATE TABLE email_metrics_monthly ( user_id NUMBER NOT NULL, campaign_id NUMBER, bucket_start DATE NOT NULL, sent_count NUMBER DEFAULT 0, open_count NUMBER DEFAULT 0, click_count NUMBER DEFAULT 0, bounce_count NUMBER DEFAULT 0, order_count NUMBER DEFAULT 0, revenue_amount NUMBER(18,4) DEFAULT 0, PRIMARY KEY(user_id, bucket_start, campaign_id) ) PARTITION BY RANGE (bucket_start) INTERVAL (NUMTOYMINTERVAL(1,''YEAR'')) ( PARTITION p0 VALUES LESS THAN (TO_DATE(''2025-01-01'',''YYYY-MM-DD'')) )'; EXCEPTION WHEN e_exists THEN NULL; END;
END;
/

------------------------------------------------------------
-- 2. MATERIALIZED VIEWS
------------------------------------------------------------
-- Rolling 30-day engagement metrics (FAST refresh once MV logs added)
DECLARE
  e_exists EXCEPTION; PRAGMA EXCEPTION_INIT(e_exists,-955);
  e_mv_exists EXCEPTION; PRAGMA EXCEPTION_INIT(e_mv_exists,-12006);
BEGIN
  BEGIN
    EXECUTE IMMEDIATE 'CREATE MATERIALIZED VIEW mv_user_engagement_30d BUILD IMMEDIATE REFRESH COMPLETE ON DEMAND AS SELECT user_id, TRUNC(event_time) AS event_day, SUM(CASE WHEN event_type=''SENT'' THEN 1 ELSE 0 END) sent_count, SUM(CASE WHEN event_type=''OPEN'' THEN 1 ELSE 0 END) open_count, SUM(CASE WHEN event_type=''CLICK'' THEN 1 ELSE 0 END) click_count, SUM(CASE WHEN event_type=''BOUNCE'' THEN 1 ELSE 0 END) bounce_count FROM email_events WHERE event_time >= TRUNC(SYSDATE) - 30 GROUP BY user_id, TRUNC(event_time)';
  EXCEPTION
    WHEN e_exists THEN NULL;
    WHEN e_mv_exists THEN NULL;
  END;
  BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_mv_engage30_user_day ON mv_user_engagement_30d(user_id, event_day)';
  EXCEPTION
    WHEN e_exists THEN NULL;
  END;
END;
/

-- Period-over-period comparison MV (current vs previous period aggregated)
DECLARE
  e_exists EXCEPTION; PRAGMA EXCEPTION_INIT(e_exists,-955);
  e_mv_exists EXCEPTION; PRAGMA EXCEPTION_INIT(e_mv_exists,-12006);
BEGIN
  BEGIN
    EXECUTE IMMEDIATE 'CREATE MATERIALIZED VIEW mv_user_period_compare BUILD IMMEDIATE REFRESH COMPLETE ON DEMAND AS WITH base AS ( SELECT user_id, TRUNC(event_time) event_day, SUM(CASE WHEN event_type=''SENT'' THEN 1 ELSE 0 END) sent_count, SUM(CASE WHEN event_type=''OPEN'' THEN 1 ELSE 0 END) open_count, SUM(CASE WHEN event_type=''CLICK'' THEN 1 ELSE 0 END) click_count FROM email_events WHERE event_time >= TRUNC(SYSDATE) - 60 GROUP BY user_id, TRUNC(event_time) ), cur AS ( SELECT user_id, SUM(sent_count) sent_cur, SUM(open_count) open_cur, SUM(click_count) click_cur FROM base WHERE event_day >= TRUNC(SYSDATE) - 30 GROUP BY user_id ), prev AS ( SELECT user_id, SUM(sent_count) sent_prev, SUM(open_count) open_prev, SUM(click_count) click_prev FROM base WHERE event_day < TRUNC(SYSDATE) - 30 GROUP BY user_id ) SELECT c.user_id, c.sent_cur, p.sent_prev, c.open_cur, p.open_prev, c.click_cur, p.click_prev FROM cur c LEFT JOIN prev p ON c.user_id = p.user_id';
  EXCEPTION
    WHEN e_exists THEN NULL;
    WHEN e_mv_exists THEN NULL;
  END;
  BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_mv_period_compare_user ON mv_user_period_compare(user_id)';
  EXCEPTION
    WHEN e_exists THEN NULL;
  END;
END;
/

------------------------------------------------------------
-- 3. INDEX STRATEGY
------------------------------------------------------------
DECLARE
  e_exists EXCEPTION; PRAGMA EXCEPTION_INIT(e_exists,-955); -- name already used
  e_dupcols EXCEPTION; PRAGMA EXCEPTION_INIT(e_dupcols,-1408); -- such column list already indexed
BEGIN
  BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_em_hourly_user_time ON email_metrics_hourly(user_id, bucket_start)';
  EXCEPTION
    WHEN e_exists THEN NULL;
    WHEN e_dupcols THEN NULL;
  END;
  BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_em_daily_user_time ON email_metrics_daily(user_id, bucket_start)';
  EXCEPTION
    WHEN e_exists THEN NULL;
    WHEN e_dupcols THEN NULL;
  END;
  BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_em_weekly_user_time ON email_metrics_weekly(user_id, bucket_start)';
  EXCEPTION
    WHEN e_exists THEN NULL;
    WHEN e_dupcols THEN NULL;
  END;
  BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_em_monthly_user_time ON email_metrics_monthly(user_id, bucket_start)';
  EXCEPTION
    WHEN e_exists THEN NULL;
    WHEN e_dupcols THEN NULL;
  END;
END;
/

------------------------------------------------------------
-- 4. FORECASTING STAGING TABLE
------------------------------------------------------------
DECLARE
  e_exists EXCEPTION; PRAGMA EXCEPTION_INIT(e_exists,-955);
  e_dupcols EXCEPTION; PRAGMA EXCEPTION_INIT(e_dupcols,-1408);
BEGIN
  BEGIN
    EXECUTE IMMEDIATE 'CREATE TABLE email_metrics_forecast_stage ( user_id NUMBER NOT NULL, metric_code VARCHAR2(32) NOT NULL, bucket_start DATE NOT NULL, metric_value NUMBER(18,6) NOT NULL, model_version VARCHAR2(32), created_at DATE DEFAULT SYSDATE, PRIMARY KEY(user_id, metric_code, bucket_start) )';
  EXCEPTION
    WHEN e_exists THEN NULL;
  END;
  BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_forecast_stage_user_metric ON email_metrics_forecast_stage(user_id, metric_code, bucket_start)';
  EXCEPTION
    WHEN e_exists THEN NULL;
    WHEN e_dupcols THEN NULL;
  END;
END;
/

------------------------------------------------------------
-- 5. PARTITION MANAGEMENT GUIDANCE (documentation only)
------------------------------------------------------------
-- Example: drop partitions older than 25 months (run via scheduled job)
-- ALTER TABLE email_metrics_daily DROP PARTITION FOR (TO_DATE('2023-06-01','YYYY-MM-DD'));

-- Add MV LOGS for FAST refresh (optional):
-- CREATE MATERIALIZED VIEW LOG ON email_events WITH ROWID, SEQUENCE (user_id, campaign_id, event_type, event_time) INCLUDING NEW VALUES;

