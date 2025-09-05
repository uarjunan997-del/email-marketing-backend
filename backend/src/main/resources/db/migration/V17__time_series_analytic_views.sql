-- Analytic views & MV log for moving averages and faster refresh

-- Materialized view log (enables FAST refresh for related MVs; ignore error if already exists)
-- NOTE: If this fails due to existing log, manually drop or skip; Flyway will record success only if executable.
CREATE MATERIALIZED VIEW LOG ON email_events WITH ROWID, SEQUENCE (user_id, campaign_id, event_type, event_time) INCLUDING NEW VALUES;

-- View providing daily engagement & moving averages using analytic window functions
CREATE OR REPLACE VIEW vw_user_daily_engagement_ma AS
SELECT user_id,
       event_day,
       sent_count,
       open_count,
       click_count,
       bounce_count,
       CASE WHEN sent_count=0 THEN 0 ELSE open_count/sent_count END AS open_rate,
       CASE WHEN sent_count=0 THEN 0 ELSE click_count/sent_count END AS click_rate,
       AVG(CASE WHEN sent_count=0 THEN 0 ELSE open_count/sent_count END)
         OVER (PARTITION BY user_id ORDER BY event_day ROWS BETWEEN 6 PRECEDING AND CURRENT ROW) AS ma7_open_rate,
       AVG(CASE WHEN sent_count=0 THEN 0 ELSE open_count/sent_count END)
         OVER (PARTITION BY user_id ORDER BY event_day ROWS BETWEEN 27 PRECEDING AND CURRENT ROW) AS ma28_open_rate,
       AVG(CASE WHEN sent_count=0 THEN 0 ELSE open_count/sent_count END)
         OVER (PARTITION BY user_id ORDER BY event_day ROWS BETWEEN 83 PRECEDING AND CURRENT ROW) AS ma84_open_rate
FROM (
  SELECT user_id,
         TRUNC(event_time) AS event_day,
         SUM(CASE WHEN event_type='SENT' THEN 1 ELSE 0 END) sent_count,
         SUM(CASE WHEN event_type='OPEN' THEN 1 ELSE 0 END) open_count,
         SUM(CASE WHEN event_type='CLICK' THEN 1 ELSE 0 END) click_count,
         SUM(CASE WHEN event_type='BOUNCE' THEN 1 ELSE 0 END) bounce_count
  FROM email_events
  WHERE event_time >= TRUNC(SYSDATE) - 400
  GROUP BY user_id, TRUNC(event_time)
);

-- Optional synonym or grant statements could be added here for BI tools.
