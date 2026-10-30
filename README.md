# FlakyHunter

**FlakyHunter finds flaky tests.** Send it the test results from every CI run (JUnit XML or Playwright JSON)
and it tracks each test across runs, scores how flaky it is, separates flaky tests from genuinely broken ones,
groups failures by root cause, and lets teams quarantine bad tests so they stop blocking builds.

> 🚧 Work in progress — see [docs/PLAN.md](docs/PLAN.md) for the full plan and progress.

**Live:** <https://flakyhunter.vercel.app> · API health: <https://flakyhunter.vercel.app/api/actuator/health>
(the free backend sleeps when idle — the first visit can take about a minute)

## Tech stack

- **Backend:** Java 25, Spring Boot 4, PostgreSQL (Neon), Flyway — hosted on Render
- **Frontend:** React + TypeScript + Vite — hosted on Vercel
- **CI:** GitHub Actions

## Repository layout

| Folder | What's inside |
| --- | --- |
| `backend/` | Spring Boot API |
| `frontend/` | React web app |
| `docs/` | Plan and architecture decision records (`docs/adr/`) |

## Run the backend locally

```bash
cd backend
mvn spring-boot:run
```

Configuration comes from environment variables — see [.env.example](.env.example).
