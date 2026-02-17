-- IdempotencyRecord: stores idempotency key + payload hash for create/confirm
CREATE TABLE idempotency_record (
    id BIGSERIAL PRIMARY KEY,
    merchant_id BIGINT NOT NULL REFERENCES merchant(id) ON DELETE CASCADE,
    idempotency_key VARCHAR(255) NOT NULL,
    operation VARCHAR(32) NOT NULL,
    payment_intent_id UUID REFERENCES payment_intent(id) ON DELETE SET NULL,
    payload_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_idempotency_create ON idempotency_record(merchant_id, idempotency_key) WHERE operation = 'CREATE';
CREATE UNIQUE INDEX idx_idempotency_confirm ON idempotency_record(merchant_id, idempotency_key, payment_intent_id) WHERE operation = 'CONFIRM';
