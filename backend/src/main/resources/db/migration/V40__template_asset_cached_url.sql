-- Flyway migration V33: Add cached read URL and expiry to template_assets
BEGIN
  BEGIN EXECUTE IMMEDIATE 'ALTER TABLE template_assets ADD (cached_read_url VARCHAR2(2000))'; EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-955, -1430) THEN RAISE; END IF; END;
  BEGIN EXECUTE IMMEDIATE 'ALTER TABLE template_assets ADD (cached_read_expires_at TIMESTAMP)'; EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-955, -1430) THEN RAISE; END IF; END;
  -- Optional index for expiry-based scans/cleanup
  BEGIN EXECUTE IMMEDIATE 'CREATE INDEX idx_template_assets_cached_expiry ON template_assets(cached_read_expires_at)'; EXCEPTION WHEN OTHERS THEN IF SQLCODE NOT IN (-955, -1408) THEN RAISE; END IF; END;
END;
