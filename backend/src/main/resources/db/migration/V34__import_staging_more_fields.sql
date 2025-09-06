-- Add additional parsed columns for common contact fields
ALTER TABLE import_staging ADD (
  parsed_phone VARCHAR2(50),
  parsed_country VARCHAR2(100),
  parsed_city VARCHAR2(150),
  parsed_segment VARCHAR2(200),
  parsed_unsubscribed NUMBER(1)
);