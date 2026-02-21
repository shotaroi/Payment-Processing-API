# Payment Processing API

A portfolio-grade Stripe-lite Payment Processing REST API built with Spring Boot 3.4 (Java 21) for banks and fintech applications. (Spring Boot 4 support can be added when ecosystem dependencies are fully compatible.)

## Overview

This API supports:
- **Merchant registration/login** (JWT)
- **Payment intents** (authorize → confirm → capture)
- **Canceling payments**
- **Asynchronous provider callbacks** (webhook simulation)
- **Idempotency** to prevent double-charging
- **Ledger/audit trail** for every state change
- **Rate limiting** per API key (Redis)

## Architecture

### Authentication

| Endpoint Type | Auth Method | Use Case |
|---------------|-------------|----------|
| `/api/auth/*` | None (public) | Register, login |
| `/api/apikeys/*`, `/api/events/*`, `/api/admin/*` | **JWT** (Bearer token) | Dashboard, merchant self-service |
| `/api/payment_intents/*` | **API Key** (X-API-KEY header) | Server-to-server payment processing |
| `/api/webhooks/*` | None (or shared secret) | Provider callbacks |

### Payment State Machine

```
CREATED ──┬──> PROCESSING ──┬──> SUCCEEDED
          │                  └──> FAILED
          └──> CANCELED

Terminal states: SUCCEEDED, FAILED, CANCELED
```

Valid transitions:
- `CREATED` → `PROCESSING`, `CANCELED`
- `PROCESSING` → `SUCCEEDED`, `FAILED`
- Cancel not allowed after `SUCCEEDED`

## How to Run

### Prerequisites

- Java 21
- Maven 3.9+
- Docker & Docker Compose

### 1. Start Infrastructure

```bash
docker-compose up -d
```

### 2. Run the Application

```bash
./mvnw spring-boot:run
```

The API runs at `http://localhost:8080`. Swagger UI: `http://localhost:8080/swagger-ui.html`

### 3. Run Tests

```bash
./mvnw test
```

Tests use Testcontainers (Postgres + Redis).

## Sample cURL Flow

### 1. Register

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"name":"Acme Corp","email":"acme@example.com","password":"securepass123"}'
```

### 2. Login

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"acme@example.com","password":"securepass123"}'
```

Response: `{"accessToken":"eyJhbGciOiJIUzI1NiJ9..."}`

### 3. Create API Key

```bash
export JWT="<accessToken from login>"
curl -X POST http://localhost:8080/api/apikeys \
  -H "Authorization: Bearer $JWT"
```

Response: `{"id":1,"apiKey":"pk_xxxx...","keyPrefix":"pk_xxxx","message":"Store this key securely..."}`

**Save the `apiKey` – it is shown only once.**

### 4. Create Payment Intent

```bash
export API_KEY="<apiKey from step 3>"
export IDEM_KEY=$(uuidgen)

curl -X POST http://localhost:8080/api/payment_intents \
  -H "X-API-KEY: $API_KEY" \
  -H "Idempotency-Key: $IDEM_KEY" \
  -H "Content-Type: application/json" \
  -d '{"amount":100.50,"currency":"SEK","description":"Order #123"}'
```

### 5. Confirm Payment

```bash
export INTENT_ID="<id from create response>"
export CONFIRM_IDEM=$(uuidgen)

curl -X POST http://localhost:8080/api/payment_intents/$INTENT_ID/confirm \
  -H "X-API-KEY: $API_KEY" \
  -H "Idempotency-Key: $CONFIRM_IDEM" \
  -H "Content-Type: application/json" \
  -d '{"paymentMethodType":"CARD","paymentMethodToken":"tok_test_visa"}'
```

### 6. Simulate Webhook (Provider Callback)

```bash
export PROVIDER_PAYMENT_ID="<providerPaymentId from confirm response>"

curl -X POST http://localhost:8080/api/webhooks/provider \
  -H "Content-Type: application/json" \
  -d '{"providerPaymentId":"'$PROVIDER_PAYMENT_ID'","status":"SUCCEEDED"}'
```

### 7. Get Payment Intent

```bash
curl -X GET "http://localhost:8080/api/payment_intents/$INTENT_ID" \
  -H "X-API-KEY: $API_KEY"
```

### 8. List Payment Events (JWT)

```bash
curl -X GET "http://localhost:8080/api/events/payment_intents/$INTENT_ID" \
  -H "Authorization: Bearer $JWT"
```

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `jwt.secret` | (dev default) | JWT signing key (256-bit) |
| `rate-limit.requests-per-minute` | 60 | Per API key |
| `payment.provider.simulate-success` | true | Dev: always succeed |
| `webhook.provider-secret` | (dev default) | Shared secret for webhooks |

## Error Responses

All errors return consistent JSON:

```json
{
  "timestamp": "2025-02-16T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/payment_intents",
  "fieldErrors": [{"field": "amount", "message": "Amount must be at least 0.01"}]
}
```

## Testing

```bash
mvn test
```

- **Unit tests**: `PaymentStateMachineTest`, `IdempotencyTest` (always run)
- **Integration tests**: Require Docker for Testcontainers (Postgres + Redis). Run with `mvn verify` or `mvn test`. Some integration tests may be disabled due to MockMvc/FilterChain compatibility; core flows can be verified via the cURL examples above.
