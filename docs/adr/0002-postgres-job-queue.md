# ADR-0002: PostgreSQL-backed job queue instead of a message broker

**Status:** Accepted

## Context
Report uploads must return quickly (`202 Accepted`) and be processed in the background, exactly once,
even when CI retries the upload. Free hosting gives us PostgreSQL but no Redis/Kafka/RabbitMQ.

## Decision
Store uploads in an `ingestion_jobs` table and let a worker claim jobs with
`SELECT ... WHERE status = 'QUEUED' FOR UPDATE SKIP LOCKED LIMIT n`.
- `unique(project_id, idempotency_key)` + `INSERT ... ON CONFLICT DO NOTHING` makes re-uploads harmless.
- `pg_advisory_xact_lock(project)` processes one job per project at a time, so stats rows don't race.
- Jobs stuck in `PROCESSING` for more than 5 minutes go back to `QUEUED`; after 3 attempts they are `FAILED`.

## Consequences
- No extra infrastructure; the queue is transactional with the data it writes.
- Throughput is limited by the database — far more than this project needs.
