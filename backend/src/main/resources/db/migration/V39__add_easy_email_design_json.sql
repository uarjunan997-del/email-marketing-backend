-- V39: Add easy_email_design_json column to email_templates to store structured easy-email design JSON
-- Oracle-safe: check user_tab_columns before adding.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns 
      WHERE table_name = 'EMAIL_TEMPLATES' AND column_name = 'EASY_EMAIL_DESIGN_JSON';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE email_templates ADD (easy_email_design_json CLOB)';
    END IF;
END;
/