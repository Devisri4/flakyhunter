import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { useMe } from '../../api/hooks'
import { describeHealth, fetchHealth } from '../../health'

const FEATURES = [
  { icon: '📥', title: 'Upload from any CI', text: 'JUnit XML (Maven, Gradle, pytest) or Playwright JSON — a 4-line GitHub Action or one curl command.' },
  { icon: '↯', title: 'Flaky or broken?', text: 'Retries, pass/fail flips and same-commit results add up to an explainable 0–1 score per test.' },
  { icon: '🧩', title: 'Root-cause groups', text: '"Timeout 3000ms on #pay-7" and "Timeout 5000ms on #pay-42" are recognised as one problem.' },
  { icon: '🧊', title: 'Quarantine', text: 'Take a flaky test out of CI for a while, with a reason and an expiry. The JUnit 5 extension skips it.' },
  { icon: '🔔', title: 'Alerts', text: 'Slack, Discord or signed webhooks the moment a test becomes flaky.' },
  { icon: '📈', title: 'Trends', text: 'Pass rate, runs per day and newly flaky tests, compared with the previous period.' },
]

export function LandingPage() {
  const me = useMe()
  const [lines, setLines] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    fetchHealth()
      .then((health) => setLines(describeHealth(health)))
      .catch((e) => setError(e.message))
  }, [])

  return (
    <>
      <section className="hero">
        <img src="/logo.svg" alt="" width={84} height={84} className="hero-logo" />
        <div>
          <span className="eyebrow">Flaky test detection for CI</span>
        </div>
        <h1>
          Flaky<span className="accent">Hunter</span>
        </h1>
        <p className="lead">
          Send your CI test results, find the flaky tests, and quarantine them so they stop blocking builds.
        </p>
        <div className="row center">
          {me.data ? (
            <Link to="/projects" className="button big">
              Go to my projects
            </Link>
          ) : (
            <>
              <Link to="/p/demo" className="button big">
                View live demo
              </Link>
              <Link to="/signup" className="button ghost big">
                Create a free account
              </Link>
            </>
          )}
        </div>
        <p className="muted small">The demo needs no account: a fictional shop with 300 tests and 60 days of CI runs.</p>
      </section>

      <section className="features" aria-label="Features">
        {FEATURES.map((f) => (
          <article key={f.title} className="card feature">
            <span className="icon" aria-hidden="true">
              {f.icon}
            </span>
            <h3>{f.title}</h3>
            <p>{f.text}</p>
          </article>
        ))}
      </section>

      <section className="card">
        <h2>How it works</h2>
        <ol className="steps">
          <li>
            <div>
              <strong>Create a project</strong>
              <div className="muted small">and an API key for your CI pipeline.</div>
            </div>
          </li>
          <li>
            <div>
              <strong>Upload after every run</strong>
              <div className="muted small">the GitHub Action sends your test reports, even when tests fail.</div>
            </div>
          </li>
          <li>
            <div>
              <strong>Fix or quarantine</strong>
              <div className="muted small">FlakyHunter shows which tests are flaky, broken, or just new.</div>
            </div>
          </li>
        </ol>
      </section>

      <section className="card">
        <div className="status-line">
          <strong>Backend status</strong>
          {error && <span className="down">Could not reach the API: {error}</span>}
          {!error && !lines && <span className="muted">Waking up the API… (the free server can take up to a minute)</span>}
          {lines?.map((line) => (
            <span key={line} className={line.endsWith('UP') ? 'up' : 'down'}>
              {line.endsWith('UP') ? '✅' : '❌'} {line}
            </span>
          ))}
          <a href="/api/docs" className="small">
            API docs ↗
          </a>
        </div>
      </section>
    </>
  )
}
