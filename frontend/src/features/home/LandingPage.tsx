import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { useMe } from '../../api/hooks'
import { describeHealth, fetchHealth } from '../../health'

export function LandingPage() {
  const me = useMe()
  const [lines, setLines] = useState<string[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchHealth()
      .then((health) => setLines(describeHealth(health)))
      .catch((e: Error) => setError(e.message))
  }, [])

  return (
    <>
      <section className="hero">
        <img src="/logo.svg" alt="" width={72} height={72} />
        <h1>
          Flaky<span className="accent">Hunter</span>
        </h1>
        <p>Send your CI test results, find the flaky tests, quarantine them so they stop blocking builds.</p>
        <div className="row center">
          {me.data ? (
            <Link to="/projects" className="button">
              Go to my projects
            </Link>
          ) : (
            <>
              <Link to="/signup" className="button">
                Create a free account
              </Link>
              <Link to="/login" className="button ghost">
                Log in
              </Link>
            </>
          )}
        </div>
      </section>

      <section className="card">
        <h2>Backend status</h2>
        {error && <p className="down">Could not reach the API: {error}</p>}
        {!error && !lines && <p className="muted">Waking up the API… (the free server can take up to a minute)</p>}
        {lines?.map((line) => (
          <p key={line} className={line.endsWith('UP') ? 'up' : 'down'}>
            {line.endsWith('UP') ? '✅' : '❌'} {line}
          </p>
        ))}
      </section>
    </>
  )
}
