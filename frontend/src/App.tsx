import { useEffect, useState } from 'react'
import { describeHealth, fetchHealth } from './health'

export default function App() {
  const [lines, setLines] = useState<string[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchHealth()
      .then((health) => setLines(describeHealth(health)))
      .catch((e: Error) => setError(e.message))
  }, [])

  return (
    <main>
      <header className="brand">
        <img src="/logo.svg" alt="" width={48} height={48} />
        <div>
          <h1>
            Flaky<span className="accent">Hunter</span>
          </h1>
          <p>Finds flaky tests in your CI runs.</p>
        </div>
      </header>
      <section className="card">
        <h2>Backend status</h2>
        {error && <p className="down">Could not reach the API: {error}</p>}
        {!error && !lines && <p>Waking up the API… (the free server can take up to a minute)</p>}
        {lines?.map((line) => (
          <p key={line} className={line.endsWith('UP') ? 'up' : 'down'}>
            {line.endsWith('UP') ? '✅' : '❌'} {line}
          </p>
        ))}
      </section>
    </main>
  )
}
