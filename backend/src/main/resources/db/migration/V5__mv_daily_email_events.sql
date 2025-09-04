-- Materialized view for daily aggregated email events (Oracle)
-- Drops if exists pattern (manual cleanup needed on Oracle versions without IF EXISTS)
-- CREATE OR REPLACE not supported for MVs; ensure idempotency via Flyway baseline or manual drop

-- Using REFRESH COMPLETE initially; switch to FAST after adding MV LOG on email_events.
CREATE MATERIALIZED VIEW mv_email_events_daily
BUILD IMMEDIATE
REFRESH COMPLETE ON DEMAND
AS
SELECT
  user_id,
  campaign_id,
  TRUNC(event_time) AS event_day,
  SUM(CASE WHEN event_type='SENT' THEN 1 ELSE 0 END) AS sent_count,
  SUM(CASE WHEN event_type='OPEN' THEN 1 ELSE 0 END) AS open_count,
  SUM(CASE WHEN event_type='CLICK' THEN 1 ELSE 0 END) AS click_count,
  SUM(CASE WHEN event_type='BOUNCE' THEN 1 ELSE 0 END) AS bounce_count
FROM email_events
GROUP BY user_id, campaign_id, TRUNC(event_time);

CREATE INDEX idx_mv_ev_daily_user_day ON mv_email_events_daily(user_id, event_day);

-- To enable FAST refresh, consider creating materialized view logs:
-- CREATE MATERIALIZED VIEW LOG ON email_events WITH ROWID, SEQUENCE (user_id, campaign_id, event_type, event_time) INCLUDING NEW VALUES;