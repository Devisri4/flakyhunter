# FlakyHunter

**FlakyHunter finds flaky tests.** Send it the test results from every CI run (JUnit XML or Playwright JSON)
and it tracks each test across runs, scores how flaky it is, separates flaky tests from genuinely broken ones,
groups failures by root cause, and lets teams quarantine bad tests so they stop blocking builds.

> 🚧 Work in progress — see [docs/PLAN.md](docs/PLAN.md) for the full plan and progress.

**Live:** <https://flakyhunter.vercel.app> · API health: <https://flakyhunter.vercel.app/api/actuator/health>
(the free backend sleeps when idle — the first visit can take about a minute)

## Tech stack

- **Backend:** Java 25, Spring Boot 4, PostgreSQL (Neon), Flyway — hosted on Render
- **Frontend:** React (JavaScript) + Vite — hosted on Vercel
- **CI:** GitHub Actions

## Repository layout

| Folder | What's inside |
| --- | --- |
| `backend/` | Spring Boot API |
| `frontend/` | React web app |
| `docs/` | Plan and architecture decision records (`docs/adr/`) |

## Run the backend locally

The backend needs a PostgreSQL database (it uses Postgres features such as `FOR UPDATE SKIP LOCKED`,
advisory locks and arrays, so H2 can't stand in). The easiest option is a free Neon **dev branch**.
Set the variables from [.env.example](.env.example) in your shell, then:

```bash
cd backend
mvn spring-boot:run
```

Tests: `mvn test` runs the unit tests anywhere; the integration tests use Testcontainers and run
automatically when Docker is available (they always run in GitHub Actions).
