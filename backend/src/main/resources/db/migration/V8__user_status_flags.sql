-- Add missing status/flag columns for users corresponding to JPA entity boolean fields
-- Hibernate referenced users.locked causing ORA-00904 because columns weren't in initial V1 script.
ALTER TABLE users ADD (
    email_verified NUMBER(1) DEFAULT 0 NOT NULL,
    active         NUMBER(1) DEFAULT 1 NOT NULL,
    locked         NUMBER(1) DEFAULT 0 NOT NULL
);

-- Ensure any existing rows (e.g., seeded admin) have sane values (Oracle applies defaults on add, but update explicitly):
UPDATE users SET email_verified = 0 WHERE email_verified IS NULL;
UPDATE users SET active = 1 WHERE active IS NULL;
UPDATE users SET locked = 0 WHERE locked IS NULL;
