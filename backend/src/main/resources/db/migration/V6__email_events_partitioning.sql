-- Optional Oracle partitioning for email_events (execute only on Enterprise edition with partition option)
-- Adjust start boundary as needed
-- ALTER TABLE email_events MODIFY PARTITION BY RANGE (event_time) INTERVAL (NUMTOYMINTERVAL(1,'MONTH')) (PARTITION p0 VALUES LESS THAN (TO_DATE('2025-01-01','YYYY-MM-DD')));

-- Placeholder: manual execution might be required since ALTER ... MODIFY PARTITION BY is not always supported; create new partitioned table & exchange.
-- Documented for infrastructure team.