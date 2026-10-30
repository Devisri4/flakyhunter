import { Link, useParams } from 'react-router'
import { useRun, type ResultView } from '../../api/hooks'
import { formatDateTime, formatDuration, passRate, shortSha } from '../../lib/format'
import { RunCounts } from './RunsTable'

export function RunDetailPage() {
  const { slug = '', id = '' } = useParams()
  const detail = useRun(slug, id)

  if (detail.isLoading) return <p className="muted">Loading…</p>
  if (detail.error) return <p className="error">{detail.error.message}</p>
  const { run, failed, flaky, slowest } = detail.data!
  const rate = passRate(run)

  return (
    <>
      <p>
        <Link to={`/p/${slug}/runs`}>← All runs</Link>
      </p>
      <h1>Run #{run.id}</h1>

      <section className="card stats">
        <Stat label="Tests" value={String(run.total)} />
        <Stat label="Pass rate" value={rate === null ? '—' : `${rate}%`} tone={run.failed > 0 ? 'down' : 'up'} />
        <Stat label="Failed" value={String(run.failed)} tone={run.failed > 0 ? 'down' : undefined} />
        <Stat label="Flaky" value={String(run.flaky)} tone={run.flaky > 0 ? 'flaky' : undefined} />
        <Stat label="Duration" value={formatDuration(run.durationMs)} />
      </section>

      <section className="card">
        <p>
          <RunCounts run={run} />
        </p>
        <p className="muted">
          {formatDateTime(run.triggeredAt)} · branch <strong>{run.branch ?? '—'}</strong> · commit{' '}
          <code>{shortSha(run.commitSha)}</code>
          {run.ciProvider && <> · {run.ciProvider}</>}
          {run.buildUrl && (
            <>
              {' '}
              ·{' '}
              <a href={run.buildUrl} target="_blank" rel="noreferrer">
                CI build ↗
              </a>
            </>
          )}
        </p>
      </section>

      <ResultsSection
        title={`Failed tests (${failed.length})`}
        empty="No failures in this run 🎉"
        results={failed}
        tone="down"
      />
      <ResultsSection
        title={`Passed only after a retry (${flaky.length})`}
        empty="No test needed a retry."
        results={flaky}
        tone="flaky"
      />

      <section className="card">
        <h2>Slowest tests</h2>
        <table>
          <tbody>
            {slowest.map((r) => (
              <tr key={r.testCaseId}>
                <td>
                  <span className="muted">{r.suite} ›</span> {r.name}
                </td>
                <td>{formatDuration(r.durationMs)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>
    </>
  )
}

function Stat({ label, value, tone }: { label: string; value: string; tone?: 'up' | 'down' | 'flaky' }) {
  return (
    <div className="stat">
      <span className="muted">{label}</span>
      <strong className={tone}>{value}</strong>
    </div>
  )
}

function ResultsSection(props: { title: string; empty: string; results: ResultView[]; tone: 'down' | 'flaky' }) {
  return (
    <section className="card">
      <h2>{props.title}</h2>
      {props.results.length === 0 && <p className="muted">{props.empty}</p>}
      {props.results.map((r) => (
        <details key={r.testCaseId} className="result">
          <summary>
            <span className={props.tone}>{props.tone === 'down' ? '✗' : '↻'}</span>{' '}
            <span className="muted">{r.suite} ›</span> <strong>{r.name}</strong>
            {r.attempts > 1 && (
              <span className="badge">
                {r.failedAttempts}/{r.attempts} attempts failed
              </span>
            )}
          </summary>
          {r.failureType && <p className="muted">{r.failureType}</p>}
          {r.message && <p>{r.message}</p>}
          {r.stack && <pre>{r.stack}</pre>}
        </details>
      ))}
    </section>
  )
}
