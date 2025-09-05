-- Rename VALUE column to BENCH_VALUE to avoid reserved word conflicts and align with entity mapping
-- Safe to run only if column VALUE exists
DECLARE
  v_count INTEGER;
BEGIN
  SELECT COUNT(*) INTO v_count FROM USER_TAB_COLUMNS 
    WHERE TABLE_NAME='ROI_BENCHMARKS' AND COLUMN_NAME='BENCH_VALUE';
  IF v_count = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE ROI_BENCHMARKS RENAME COLUMN VALUE TO BENCH_VALUE';
  END IF;
END;
/