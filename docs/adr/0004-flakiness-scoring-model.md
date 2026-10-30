# ADR-0004: Flakiness scoring model

**Status:** Accepted

## Context
We must tell **flaky** tests (pass and fail without a code change) apart from **broken** tests (a real
regression), and users must be able to see *why* a test got its label.

## Decision
For each test, look at its last 30 executed runs (skips ignored; fewer than 5 runs ⇒ `NEW`). Three signals:

| Signal | Meaning | Calculation |
| --- | --- | --- |
| R | Passed on retry in the same run | `retry_pass_count / runs_in_window` |
| F | Flips between pass and fail | `flip_count / (runs_in_window − 1)` |
| C | Same commit both passed and failed | `inconsistent_commits / commits_with_2+_runs` |

`score = min(1, 0.45·min(1, 2R) + 0.35·min(1, 2F) + 0.20·C)`

States, checked in order: `NEW` (< 5 runs) → `BROKEN` (failed the last 3 runs with no retry-pass) →
`FLAKY` (score ≥ 0.30) → `SUSPECT` (score ≥ 0.10 or any retry-pass in the last 10 runs) → `STABLE`.

## Consequences
- A real regression gives a fail streak with only two flips, so it is reported as `BROKEN`, not flaky.
- Simple and explainable; the UI shows the R/F/C breakdown.
- Weights and thresholds are configuration (`flakyhunter.scoring.*`); a nightly job recomputes everything.
