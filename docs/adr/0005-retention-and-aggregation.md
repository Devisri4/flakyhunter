# ADR-0005: Data retention and aggregation for free-tier limits

**Status:** Accepted

## Context
The free Neon database has about 0.5–1 GB of storage. Test results grow with every CI run; error messages
and stack traces are the largest part.

## Decision
- Keep per-test **stats columns** on `test_cases` and per-day totals in `daily_stats`, so dashboards never
  scan raw history.
- Nightly: clear `message`/`stack` on results older than 30 days; delete `test_results` older than 180 days.
- Delete upload payloads from `ingestion_jobs` once processed.
- `daily_stats` is kept forever (it is tiny).

## Consequences
- Storage stays small and dashboard queries stay fast.
- Old individual failures lose their stack traces; signatures and trends remain.
