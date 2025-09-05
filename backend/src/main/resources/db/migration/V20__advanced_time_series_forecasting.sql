-- Forecast staging table for daily open rate forecasts (Holt-Winters additive)
CREATE TABLE email_metrics_daily_forecast (
    user_id NUMBER NOT NULL,
    bucket_start TIMESTAMP NOT NULL,
    open_rate_forecast NUMBER(10,6) NOT NULL,
    model VARCHAR2(40) DEFAULT 'HW_ADD' NOT NULL,
    created_at TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    PRIMARY KEY(user_id, bucket_start, model)
);
CREATE INDEX idx_em_df_user_created ON email_metrics_daily_forecast(user_id, created_at);