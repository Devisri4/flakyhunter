# ADR-0001: Modular monolith over microservices

**Status:** Accepted

## Context
FlakyHunter is built by one person and hosted on free tiers (one Render web service, one Neon database).
Microservices would need several deployables, a network between them and more operational work.

## Decision
Build **one Spring Boot application** split into feature modules (`auth`, `project`, `ingestion`, `parsing`,
`analysis`, `catalog`, `dashboard`, `quarantine`, `notification`, `demo`). Modules talk only through public
APIs; this is enforced by ArchUnit tests.

## Consequences
- One deploy, one database, simple free-tier hosting.
- Boundaries are still visible and tested, so a module (e.g. ingestion) could be extracted later.
- All modules scale together; acceptable for this workload.
