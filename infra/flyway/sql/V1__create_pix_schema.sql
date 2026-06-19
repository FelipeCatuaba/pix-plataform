CREATE SCHEMA pix;

-- Extensoes usadas para identificadores do outbox.
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE pix.pix_transaction (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(100) NOT NULL UNIQUE,
    amount NUMERIC(19, 2) NOT NULL,
    pix_key VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    status VARCHAR(30) NOT NULL,
    partner_reference_id VARCHAR(100),
    failure_reason TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE pix.outbox_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP
);

CREATE TABLE pix.pix_processing_attempt (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(100) NOT NULL,
    attempt_number INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    error_code VARCHAR(100),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_pix_processing_attempt_transaction_number
        UNIQUE (transaction_id, attempt_number),
    CONSTRAINT fk_pix_processing_attempt_transaction
        FOREIGN KEY (transaction_id)
        REFERENCES pix.pix_transaction (transaction_id)
);

CREATE INDEX idx_pix_transaction_status
    ON pix.pix_transaction (status);

CREATE INDEX idx_outbox_event_status_created_at
    ON pix.outbox_event (status, created_at);

CREATE INDEX idx_processing_attempt_transaction_id
    ON pix.pix_processing_attempt (transaction_id);
