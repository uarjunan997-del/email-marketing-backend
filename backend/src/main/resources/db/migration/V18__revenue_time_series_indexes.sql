-- Indexes to accelerate revenue & order aggregation joins for time-series rollups
CREATE INDEX idx_ra_created_at ON revenue_attribution(created_at);
CREATE INDEX idx_ra_user_campaign_created ON revenue_attribution(user_id, campaign_id, created_at);