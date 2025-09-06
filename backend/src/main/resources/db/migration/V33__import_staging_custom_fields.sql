-- Add parsed_custom_fields column to store JSON built from CSV mapping
ALTER TABLE import_staging ADD (parsed_custom_fields CLOB);

-- Index is not necessary for this column; it's used only during processing