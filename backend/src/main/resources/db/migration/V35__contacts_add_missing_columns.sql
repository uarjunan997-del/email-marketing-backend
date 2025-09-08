-- Align legacy contacts table (from early migration) with current schema expected by the app
-- Adds missing columns and indexes idempotently for Oracle

DECLARE
  v_cnt NUMBER;
  PROCEDURE add_col(p_col VARCHAR2, p_ddl VARCHAR2) IS
  BEGIN
    SELECT COUNT(*) INTO v_cnt FROM user_tab_cols WHERE table_name = 'CONTACTS' AND column_name = UPPER(p_col);
    IF v_cnt = 0 THEN EXECUTE IMMEDIATE p_ddl; END IF;
  END;
BEGIN
  -- Add missing columns
  add_col('PHONE',               'ALTER TABLE contacts ADD (phone VARCHAR2(50))');
  add_col('COUNTRY',             'ALTER TABLE contacts ADD (country VARCHAR2(100))');
  add_col('CITY',                'ALTER TABLE contacts ADD (city VARCHAR2(150))');
  add_col('UPDATED_AT',          'ALTER TABLE contacts ADD (updated_at TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP)');
  add_col('CUSTOM_FIELDS',       'ALTER TABLE contacts ADD (custom_fields CLOB)');
  add_col('IS_DELETED',          'ALTER TABLE contacts ADD (is_deleted NUMBER(1) DEFAULT 0)');
  add_col('DELETE_REQUESTED_AT', 'ALTER TABLE contacts ADD (delete_requested_at TIMESTAMP WITH TIME ZONE)');

  -- Widen existing columns to match current expectations (ignore if already sufficient)
  BEGIN EXECUTE IMMEDIATE 'ALTER TABLE contacts MODIFY (first_name VARCHAR2(200))'; EXCEPTION WHEN OTHERS THEN NULL; END;
  BEGIN EXECUTE IMMEDIATE 'ALTER TABLE contacts MODIFY (last_name  VARCHAR2(200))'; EXCEPTION WHEN OTHERS THEN NULL; END;
  BEGIN EXECUTE IMMEDIATE 'ALTER TABLE contacts MODIFY (segment    VARCHAR2(200))'; EXCEPTION WHEN OTHERS THEN NULL; END;
END;
/

-- Helpful indexes (idempotent)
DECLARE
  e_exists EXCEPTION; PRAGMA EXCEPTION_INIT(e_exists,-955);
  e_dupcols EXCEPTION; PRAGMA EXCEPTION_INIT(e_dupcols,-1408);
BEGIN
  BEGIN EXECUTE IMMEDIATE 'CREATE INDEX idx_contacts_segment ON contacts(segment)'; EXCEPTION WHEN e_exists THEN NULL; WHEN e_dupcols THEN NULL; END;
  BEGIN EXECUTE IMMEDIATE 'CREATE INDEX idx_contacts_email_lower ON contacts(LOWER(email))'; EXCEPTION WHEN e_exists THEN NULL; WHEN e_dupcols THEN NULL; END;
END;
/
