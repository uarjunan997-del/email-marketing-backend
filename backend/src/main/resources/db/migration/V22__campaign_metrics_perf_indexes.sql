-- Additional performance indexes & optimizer directives for time-series queries
DECLARE
	e_exists EXCEPTION; PRAGMA EXCEPTION_INIT(e_exists,-955);
	e_dupcols EXCEPTION; PRAGMA EXCEPTION_INIT(e_dupcols,-1408);
BEGIN
	BEGIN
		EXECUTE IMMEDIATE 'CREATE INDEX IDX_CAMPAIGN_METRICS_TS_HOURLY ON CAMPAIGN_METRICS_TIMESERIES (CAMPAIGN_ID, METRIC_DATE, METRIC_HOUR)';
	EXCEPTION
		WHEN e_exists THEN NULL;
		WHEN e_dupcols THEN NULL; -- column list already indexed
	END;
END;
/

-- Example function-based index for day-of-week analytics (optional)
-- CREATE INDEX IDX_CAMPAIGN_METRICS_TS_DOW ON CAMPAIGN_METRICS_TIMESERIES (TO_CHAR(METRIC_DATE,'DY'));
