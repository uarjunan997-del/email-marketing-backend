-- V37: Add AI meta JSON column to email_templates
-- Stores generator configuration (prompt, type, style, colors, model, etc.)
-- Idempotent pattern: add only if not exists (Oracle workaround via exception block)
DECLARE
  col_count INTEGER;
BEGIN
  SELECT COUNT(*) INTO col_count FROM user_tab_cols 
    WHERE table_name = 'EMAIL_TEMPLATES' AND COLUMN_NAME = 'AI_META_JSON';
  IF col_count = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE email_templates ADD (ai_meta_json CLOB)';
  END IF;
END;
/

-- Optional index (not usually needed for CLOB; commented out)
-- CREATE INDEX idx_email_templates_ai_meta_json ON email_templates(ai_meta_json) ;
