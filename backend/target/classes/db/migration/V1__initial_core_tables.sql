-- Core auth + subscription tables (PostgreSQL dialect; adjust types for Oracle if needed)
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    currency VARCHAR(8) DEFAULT 'USD' NOT NULL,
    locale VARCHAR(16) DEFAULT 'en-US' NOT NULL
);

CREATE TABLE plans (
    id BIGSERIAL PRIMARY KEY,
    plan_type VARCHAR(32) NOT NULL,
    region VARCHAR(16) NOT NULL,
    currency VARCHAR(8) NOT NULL,
    billing_period VARCHAR(16) NOT NULL,
    amount INT NOT NULL,
    statements_per_month INT,
    pages_per_statement INT,
    features TEXT,
    combined_bank INT,
    CONSTRAINT uq_plan UNIQUE (plan_type, region, billing_period)
);

CREATE TABLE subscriptions (
    id BIGSERIAL PRIMARY KEY,
    plan_type VARCHAR(32),
    start_date TIMESTAMP,
    end_date TIMESTAMP,
    status VARCHAR(32),
    external_payment_id VARCHAR(128),
    external_order_id VARCHAR(128),
    billing_period VARCHAR(16),
    user_id BIGINT UNIQUE REFERENCES users(id)
);

-- Seed example plans
INSERT INTO plans (plan_type, region, currency, billing_period, amount, statements_per_month, pages_per_statement, features)
VALUES
 ('FREE','GLOBAL','USD','MONTHLY',0,3,10,'basic'),
 ('PRO','GLOBAL','USD','MONTHLY',999,5,50,'advanced,analytics'),
 ('PRO','GLOBAL','USD','YEARLY',9990,5,50,'advanced,analytics'),
 ('PREMIUM','GLOBAL','USD','MONTHLY',1999,100,100,'all'),
 ('PREMIUM','GLOBAL','USD','YEARLY',19990,100,100,'all')
ON CONFLICT DO NOTHING;
