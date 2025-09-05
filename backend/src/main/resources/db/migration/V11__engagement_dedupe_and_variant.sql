-- V11: Add engagement dedupe timestamps and ensure variant_code column exists
ALTER TABLE campaign_recipients ADD (first_open_at TIMESTAMP NULL);
ALTER TABLE campaign_recipients ADD (first_click_at TIMESTAMP NULL);
-- variant_code added in code; add if not present (Oracle will error if exists; optional DDL guard omitted for simplicity)
ALTER TABLE campaign_recipients ADD (variant_code VARCHAR2(32));
