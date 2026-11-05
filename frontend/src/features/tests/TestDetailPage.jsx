import { Link, useParams } from 'react-router'
import { useProject, useTestDetail, useTestResults } from '../../api/hooks'
import { TrendLine } from '../../components/Charts'
import { Skeleton } from '../../components/Skeleton'
import { StateBadge, STATE_INFO } from '../../components/TestBadges'
import { formatDateTime, formatDuration, shortSha } from '../../lib/format'
import { QuarantinePanel } from './QuarantinePanel'

const OUTCOME = {
  PASSED: { cell: 'P', icon: '✓', label: 'passed' },
  FAILED: { cell: 'F', icon: '✗', label: 'failed' },
  PASSED_ON_RETRY: { cell: 'R', icon: '↻', label: 'passed after a retry' },
  SKIPPED: { cell: 'S', icon: '⤼', label: 'skipped' },
}

/** One cell per run (oldest left), each a link to its run, with colour + icon + text label. */
function Heatmap({ slug, results }) {
  if (results.length === 0) return <p className="muted">No runs yet.</p>
  const oldestFirst = [...results].reverse()
  return (
    <>
      <div className="heatmap" role="list" aria-label={`Last ${results.length} runs, oldest first`}>
        {oldestFirst.map((r) => {
          const o = OUTCOME[r.outcome]
          const label = `Run #${r.runId}, ${formatDateTime(r.triggeredAt)}: ${o.label}`
          return (
            <Link
              key={r.runId}
              to={`/p/${slug}/runs/${r.runId}`}
              className={`hcell cell-${o.cell}`}
              title={`${label}${r.message ? ` — ${r.message}` : ''}`}
              aria-label={label}
              role="listitem"
            >
              {o.icon}
            </Link>
          )
        })}
      </div>
      <p className="legend small">
        {Object.values(OUTCOME).map((o) => (
          <span key={o.cell}>
            <span className={`hcell mini cell-${o.cell}`}>{o.icon}</span> {o.label}
          </span>
        ))}
      </p>
    </>
  )
}

/** How the score is made: each signal's share of the score, next to the thresholds that decide the state. */
function ScoreBreakdown({ test, breakdown }) {
  const s = breakdown.settings
  // all bars share one scale (the largest weight), so their lengths compare honestly
  const scale = Math.max(s.retryWeight, s.flipWeight, s.commitWeight)
  const parts = [
    {
      key: 'retry',
      label: 'Passed on retry',
      points: breakdown.retryPart,
      max: s.retryWeight,
      detail: `${test.retryPassCount} of ${test.runsInWindow} runs needed a retry`,
    },
    {
      key: 'flip',
      label: 'Pass ↔ fail flips',
      points: breakdown.flipPart,
      max: s.flipWeight,
      detail: `${test.flipCount} flips in ${test.runsInWindow} runs`,
    },
    {
      key: 'commit',
      label: 'Same commit passed and failed',
      points: breakdown.commitPart,
      max: s.commitWeight,
      detail: `${Math.round(breakdown.commitSignal * 100)}% of re-run commits gave mixed results`,
    },
  ]
  return (
    <section className="card">
      <h2>Why this score?</h2>
      <p>
        Score <strong>{Number(breakdown.score).toFixed(2)}</strong> out of 1 → <StateBadge state={test.state} />
      </p>
      <table className="breakdown">
        <tbody>
          {parts.map((p) => (
            <tr key={p.key}>
              <td>
                {p.label}
                <div className="muted small">{p.detail}</div>
              </td>
              <td className="bar-cell">
                <div className="meter" role="img" aria-label={`${p.points.toFixed(2)} of at most ${p.max} points`}>
                  <div className="meter-fill" style={{ width: `${(p.points / scale) * 100}%` }} />
                </div>
              </td>
              <td className="num">
                {p.points.toFixed(2)} <span className="muted small">/ {p.max}</span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="muted small">
        Rules, checked in order: fewer than {s.minRuns} runs → New · failed the last {s.brokenStreak} runs → Broken ·
        score ≥ {s.flakyThreshold} → Flaky · score ≥ {s.suspectThreshold} or a retry in the last{' '}
        {s.suspectRetryLookback} runs → Suspect · otherwise Stable. Window: the last {s.windowSize} runs where the test
        ran.
      </p>
    </section>
  )
}

export function TestDetailPage() {
  const { slug = '', id = '' } = useParams()
  const detail = useTestDetail(slug, id)
  const results = useTestResults(slug, id, 50)
  const project = useProject(slug)

  if (detail.isLoading) return <Skeleton height={400} />
  if (detail.error) return <p className="error">{detail.error.message}</p>
  const { test, firstSeen, breakdown, durations, history, signatures, quarantine, quarantines } = detail.data
  const role = project.data?.role
  const canManage = !project.data?.publicDemo && (role === 'OWNER' || role === 'MEMBER')
  const durationData = (results.data ?? [])
    .filter((r) => r.outcome !== 'SKIPPED')
    .reverse()
    .map((r) => ({ x: r.triggeredAt, ms: r.durationMs }))

  return (
    <>
      <p>
        <Link to={`/p/${slug}/tests`}>← All tests</Link>
      </p>
      <p className="muted">{test.suite}</p>
      <h1 className="test-title">
        {test.name} <StateBadge state={test.state} />
        {test.quarantined && <span className="state state-quarantined">🧊 Quarantined</span>}
      </h1>
      <p className="muted small">
        {test.file && (
          <>
            <code>{test.file}</code> ·{' '}
          </>
        )}
        first seen {formatDateTime(firstSeen)} · last seen {formatDateTime(test.lastSeen)} ·{' '}
        {STATE_INFO[test.state].hint}
      </p>

      <section className="card">
        <h2>Last {results.data?.length ?? ''} runs</h2>
        {results.isLoading ? <Skeleton height={60} /> : <Heatmap slug={slug} results={results.data ?? []} />}
      </section>

      <ScoreBreakdown test={test} breakdown={breakdown} />

      <QuarantinePanel
        slug={slug}
        testId={id}
        active={quarantine}
        history={quarantines}
        canManage={canManage}
      />

      <section className="card">
        <h2>Duration</h2>
        <p className="muted small">
          Average {durations.avgMs === null ? '—' : formatDuration(durations.avgMs)} · 95th percentile{' '}
          {durations.p95Ms === null ? '—' : formatDuration(durations.p95Ms)} · slowest{' '}
          {durations.maxMs === null ? '—' : formatDuration(durations.maxMs)} (last 50 runs)
        </p>
        {durationData.length > 1 && (
          <TrendLine
            data={durationData}
            dataKey="ms"
            format={formatDuration}
            domain={[0, 'auto']}
            labelFormat={(iso) => new Date(iso).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}
          />
        )}
      </section>

      <div className="grid-2">
        <section className="card">
          <h2>State history</h2>
          {history.length === 0 && <p className="muted">No changes yet.</p>}
          <ul className="timeline">
            {history.map((h, i) => (
              <li key={i}>
                <span className="muted small">{formatDateTime(h.changedAt)}</span>
                <div>
                  {h.fromState ? <StateBadge state={h.fromState} /> : <span className="muted">—</span>} →{' '}
                  <StateBadge state={h.toState} /> <span className="muted small">score {Number(h.score).toFixed(2)}</span>
                  {h.runId && (
                    <>
                      {' '}
                      · <Link to={`/p/${slug}/runs/${h.runId}`}>run #{h.runId}</Link>
                    </>
                  )}
                </div>
              </li>
            ))}
          </ul>
        </section>

        <section className="card">
          <h2>Failure groups</h2>
          {signatures.length === 0 && <p className="muted">This test has no recorded failures.</p>}
          {signatures.map((s) => (
            <div key={s.id} className="signature">
              <div className="row spread">
                <strong className="small">{s.exceptionType}</strong>
                <span className="badge">{s.occurrences}×</span>
              </div>
              <code className="small">{s.normalizedMessage}</code>
              <div className="muted small">last {formatDateTime(s.lastSeen)}</div>
            </div>
          ))}
        </section>
      </div>

      {results.data && results.data.length > 0 && (
        <section className="card">
          <h2>Recent results</h2>
          <table>
            <thead>
              <tr>
                <th>Run</th>
                <th>When</th>
                <th>Commit</th>
                <th>Result</th>
                <th>Duration</th>
              </tr>
            </thead>
            <tbody>
              {results.data.slice(0, 15).map((r) => (
                <tr key={r.runId}>
                  <td>
                    <Link to={`/p/${slug}/runs/${r.runId}`}>#{r.runId}</Link>
                  </td>
                  <td>{formatDateTime(r.triggeredAt)}</td>
                  <td>
                    <code>{shortSha(r.commitSha)}</code>
                  </td>
                  <td>
                    {OUTCOME[r.outcome].icon} {OUTCOME[r.outcome].label}
                    {r.message && <div className="muted small clip">{r.message}</div>}
                  </td>
                  <td>{formatDuration(r.durationMs)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
    </>
  )
}
