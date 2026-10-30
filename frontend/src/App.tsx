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
      <h1>🐞 FlakyHunter</h1>
      <p>Finds flaky tests in your CI runs.</p>
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
