-- V38: Add system flag to template_variables and unique constraint (template_id,var_name)
-- Oracle-safe idempotent migration

-- 1. Add SYSTEM column if missing
DECLARE
	v_count INTEGER;
BEGIN
	SELECT COUNT(*) INTO v_count FROM user_tab_cols WHERE table_name = 'TEMPLATE_VARIABLES' AND column_name = 'SYSTEM';
	IF v_count = 0 THEN
		EXECUTE IMMEDIATE 'ALTER TABLE template_variables ADD (system NUMBER(1) DEFAULT 0 NOT NULL)';
	END IF;
EXCEPTION WHEN OTHERS THEN
	-- Ignore if another session added concurrently
	NULL;
END;
/

-- 2. Backfill system defaults (safe to run repeatedly)
UPDATE template_variables SET system = 1 
 WHERE var_name IN ('first_name','last_name','company_name','unsubscribe_url')
	 AND system = 0;
COMMIT;

-- 3. Create unique index if not exists
DECLARE
	v_count INTEGER;
BEGIN
	SELECT COUNT(*) INTO v_count FROM user_indexes WHERE index_name = 'UX_TEMPLATE_VARIABLES_TEMPLATE_VAR';
	IF v_count = 0 THEN
		EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX ux_template_variables_template_var ON template_variables(template_id, var_name)';
	END IF;
EXCEPTION WHEN OTHERS THEN
	-- Ignore ORA-01408 (already indexed) or race conditions
	NULL;
END;
/
