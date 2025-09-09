-- Add suppressed column to contacts table
ALTER TABLE contacts ADD suppressed NUMBER(1) DEFAULT 0 NOT NULL;

-- Create index for suppressed filtering
CREATE INDEX idx_contacts_suppressed ON contacts(suppressed);

-- Comment on the column
COMMENT ON COLUMN contacts.suppressed IS 'Indicates if contact is suppressed from receiving emails';
