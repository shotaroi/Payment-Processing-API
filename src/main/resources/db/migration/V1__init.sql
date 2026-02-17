-- Merchant
CREATE TABLE merchant (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_merchant_email ON merchant(email);

-- ApiKey
CREATE TABLE api_key (
    id BIGSERIAL PRIMARY KEY,
    merchant_id BIGINT NOT NULL REFERENCES merchant(id) ON DELETE CASCADE,
    key_prefix VARCHAR(16) NOT NULL,
    key_hash VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_api_key_merchant ON api_key(merchant_id);
CREATE INDEX idx_api_key_prefix ON api_key(key_prefix);
CREATE UNIQUE INDEX idx_api_key_prefix_status ON api_key(key_prefix) WHERE status = 'ACTIVE';

-- PaymentIntent
CREATE TABLE payment_intent (
    id UUID PRIMARY KEY,
    merchant_id BIGINT NOT NULL REFERENCES merchant(id) ON DELETE CASCADE,
    amount DECIMAL(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    description VARCHAR(500),
    customer_reference VARCHAR(255),
    idempotency_key_create VARCHAR(255),
    idempotency_key_confirm VARCHAR(255),
    provider_payment_id VARCHAR(255),
    failure_code VARCHAR(64),
    failure_message VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_payment_intent_merchant ON payment_intent(merchant_id);
CREATE INDEX idx_payment_intent_status ON payment_intent(status);
CREATE INDEX idx_payment_intent_created ON payment_intent(created_at);
CREATE UNIQUE INDEX idx_payment_intent_idempotency_create ON payment_intent(merchant_id, idempotency_key_create) WHERE idempotency_key_create IS NOT NULL;
CREATE UNIQUE INDEX idx_payment_intent_idempotency_confirm ON payment_intent(merchant_id, idempotency_key_confirm) WHERE idempotency_key_confirm IS NOT NULL;
CREATE UNIQUE INDEX idx_payment_intent_provider ON payment_intent(provider_payment_id) WHERE provider_payment_id IS NOT NULL;

-- PaymentEvent
CREATE TABLE payment_event (
    id BIGSERIAL PRIMARY KEY,
    payment_intent_id UUID NOT NULL REFERENCES payment_intent(id) ON DELETE CASCADE,
    type VARCHAR(64) NOT NULL,
    payload TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_payment_event_intent ON payment_event(payment_intent_id);
CREATE INDEX idx_payment_event_created ON payment_event(created_at);

-- WebhookDelivery
CREATE TABLE webhook_delivery (
    id BIGSERIAL PRIMARY KEY,
    payment_intent_id UUID NOT NULL REFERENCES payment_intent(id) ON DELETE CASCADE,
    event_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_webhook_delivery_intent ON webhook_delivery(payment_intent_id);

-- AuditLog
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    actor_merchant_id BIGINT REFERENCES merchant(id) ON DELETE SET NULL,
    action VARCHAR(255) NOT NULL,
    details TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_log_merchant ON audit_log(actor_merchant_id);
CREATE INDEX idx_audit_log_created ON audit_log(created_at);
