# ADR-0006: Public demo access and quarantine publishing

**Status:** Accepted

## Context
Visitors (recruiters) should explore a convincing dashboard without signing up, while every real project stays
private. Separately, FlakyHunter cannot skip tests inside someone else's CI.

## Decision
- A generated project with `is_public_demo = true` (`/p/demo`, rolling 60 days, topped up hourly) may be **read**
  by anyone. `ProjectAccess` is the only place that allows it: anonymous requests for anything else get 401 — the
  same answer for private and missing projects — and logged-in non-members keep getting 404.
- The slug `demo` (and a few others) is reserved.
- Quarantine is **published, not enforced**: `GET /api/v1/quarantine` (API key) lists fingerprints; the
  `flakyhunter-junit5` extension skips matching tests and **fails open** (FlakyHunter down ⇒ run everything);
  Playwright users get a `--grep-invert` pattern on the Setup page.
- Quarantines always expire (default 14 days, max 90), so quarantine can't become a graveyard.

## Consequences
- One code path for read access, covered by tests for anonymous, stranger, viewer and owner.
- CI behaviour stays under the team's control; a FlakyHunter outage never breaks a build.
