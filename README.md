# Real Time Notification Service

A small event driven email notification API built with Java 21 and Spring Boot. The API validates and stores a notification, publishes its ID to RabbitMQ, and returns immediately. A background worker sends the email through SMTP and updates delivery status in PostgreSQL. Redis applies an atomic per recipient rate limit.

## Features

- `POST /api/v1/notifications` queues an email and returns `202 Accepted`.
- `GET /api/v1/notifications/{id}` returns `QUEUED`, `DELIVERED`, or `FAILED`.
- `Idempotency-Key` prevents a client retry from creating a duplicate notification. Reusing a key with a different payload returns `409 Conflict`.
- Redis Lua script applies a per-recipient fixed-window rate limit; requests above the limit return `429` and `Retry-After`.
- RabbitMQ worker retries delivery up to three times with exponential backoff. Exhausted messages are recorded as failed and routed to a dead-letter queue.
- API-key filter protects application endpoints. The local default key is for development only.
- Mailpit captures messages locally so no real email provider or credentials are required.

## Architecture

```text
Client --POST--> Spring Boot API --save--> PostgreSQL
                      |                       |
                      +--rate check--> Redis  +--status lookup--> Client
                      |
                      +--publish--> RabbitMQ queue --> Email worker --> Mailpit SMTP
                                                   \--> retry / dead-letter queue
```

## Run locally

Requirements: Docker Desktop with Docker Compose.

```bash
cp .env.example .env
docker compose up --build
```

The API starts at `http://localhost:8080`. View captured emails at `http://localhost:8025`. RabbitMQ management UI is at `http://localhost:15672` (`guest` / `guest`).

The default development API key is `local-dev-key-change-me`. Set `API_KEY` in `.env` to a private value before sharing the running service.

## Send a notification

```bash
curl -i http://localhost:8080/api/v1/notifications \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: local-dev-key-change-me' \
  -H 'Idempotency-Key: order-781-confirmation' \
  -d '{"recipientEmail":"you@example.com","subject":"Order confirmed","message":"Your order has been received."}'
```

The API responds with `202 Accepted` and a JSON body containing the notification ID and `QUEUED` status. Open Mailpit to see the delivered email. Then query its status:

```bash
curl http://localhost:8080/api/v1/notifications/REPLACE_WITH_ID \
  -H 'X-API-Key: local-dev-key-change-me'
```

## Useful failure scenarios

- Send six notifications to the same recipient inside one minute: the sixth should receive `429`.
- Repeat the same request with the same `Idempotency-Key`: it returns the original notification instead of queuing a duplicate.
- Reuse that key with a different body: the API returns `409`.
- Stop Mailpit before submitting a notification: the worker retries, records the notification as failed, and routes the message to RabbitMQ's `notifications.dead-letter` queue.

## API contract

### `POST /api/v1/notifications`

Headers: `X-API-Key`, `Idempotency-Key`

```json
{
  "recipientEmail": "you@example.com",
  "subject": "Payment received",
  "message": "Your payment was received."
}
```

### `GET /api/v1/notifications/{id}`

Returns the persisted ID, delivery status, and timestamps.

## Stack

Java 21, Spring Boot, Spring AMQP, RabbitMQ, Redis, PostgreSQL, Docker Compose, Mailpit.

## Scope and next improvements

This is a portfolio MVP. The API-key filter demonstrates a simple shared secret boundary, not production user authentication. Useful next steps are an outbox pattern for atomic database to broker publishing, recipient preferences and unsubscribe handling, provider adapters, structured metrics/tracing, and automated unit/integration tests.
