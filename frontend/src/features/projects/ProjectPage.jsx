import { useState } from 'react'
import { Link, useParams } from 'react-router'
import {
  useMe,
  useNewlyFlaky,
  useProject,
  useRuns,
  useSummary,
  useTests,
  useTestSummary,
  useTrends,
  useUploads,
} from '../../api/hooks'
import { DailyBars, TrendLine } from '../../components/Charts'
import { Skeleton } from '../../components/Skeleton'
import { OutcomeStrip, StateBadge, STATE_INFO } from '../../components/TestBadges'
import { change, formatDateTime, formatDuration } from '../../lib/format'
import { RunsTable } from '../runs/RunsTable'

const HEALTH_ORDER = ['FLAKY', 'BROKEN', 'SUSPECT', 'STABLE', 'NEW']
const PERIODS = [7, 30, 90]

function Kpi({ label, value, delta, hint, tone }) {
  return (
    <div className="stat kpi" title={hint}>
      <span className="muted">{label}</span>
      <strong className={tone}>{value}</strong>
      {delta && <span className="muted small">{delta}</span>}
    </div>
  )
}

function Kpis({ slug, days }) {
  const summary = useSummary(slug, days)
  if (summary.isLoading) return <Skeleton height={92} />
  if (!summary.data) return null
  const s = summary.data
  const durationDelta =
    s.avgRunDurationMs !== null && s.previousAvgRunDurationMs !== null
      ? `${s.avgRunDurationMs >= s.previousAvgRunDurationMs ? '+' : '−'}${formatDuration(Math.abs(s.avgRunDurationMs - s.previousAvgRunDurationMs))} vs previous ${days} days`
      : null
  const passDelta = change(s.passRate, s.previousPassRate)
  return (
    <section className="stats kpis">
      <Kpi
        label="Pass rate"
        value={s.passRate === null ? '—' : `${s.passRate}%`}
        delta={passDelta && `${passDelta} vs previous ${days} days`}
        hint="Passed (incl. passed on retry) ÷ executed tests; skipped tests are left out"
        tone={s.passRate !== null && s.passRate < 95 ? 'down' : undefined}
      />
      <Kpi
        label="Flaky tests"
        value={String(s.flakyTests)}
        delta={`${s.newlyFlakyTests} new this week · ${s.brokenTests} broken`}
        hint="Tests whose current verdict is FLAKY"
        tone={s.flakyTests > 0 ? 'flaky' : undefined}
      />
      <Kpi label="Runs per day" value={String(s.runsPerDay)} delta={`${s.runs} runs in ${days} days`} />
      <Kpi
        label="Avg run duration"
        value={s.avgRunDurationMs === null ? '—' : formatDuration(s.avgRunDurationMs)}
        delta={durationDelta}
        hint="Sum of test durations per run, averaged"
      />
    </section>
  )
}

function Trends({ slug, days }) {
  const trends = useTrends(slug, days)
  if (trends.isLoading) return <Skeleton height={220} />
  if (!trends.data) return null
  const data = trends.data.map((t) => ({ ...t, x: t.day }))
  if (data.every((d) => d.runs === 0)) return null
  return (
    <div className="grid-2">
      <section className="card">
        <h2>Pass rate per day</h2>
        <TrendLine data={data} dataKey="passRate" format={(v) => `${v}%`} domain={['auto', 100]} />
      </section>
      <section className="card">
        <h2>Runs per day</h2>
        <DailyBars data={data} dataKey="runs" format={(v) => `${v} run${v === 1 ? '' : 's'}`} />
        <p className="muted small">
          {data.reduce((a, d) => a + d.flaky, 0)} results passed only after a retry in this period.
        </p>
      </section>
    </div>
  )
}

/** Counts per state (each links to the filtered test list) and the most flaky tests. */
function TestHealth({ slug }) {
  const summary = useTestSummary(slug)
  const flaky = useTests(slug, { state: 'FLAKY', size: 10 })
  if (summary.isLoading) return <Skeleton height={160} />
  if (!summary.data) return null
  const total = Object.values(summary.data).reduce((a, b) => a + b, 0)
  if (total === 0) return null

  return (
    <section className="card">
      <h2>Test health</h2>
      <div className="stats">
        {HEALTH_ORDER.map((s) => (
          <Link key={s} to={`/p/${slug}/tests?state=${s}`} className={`stat stat-link state-${s.toLowerCase()}`}>
            <span className="muted">
              {STATE_INFO[s].icon} {STATE_INFO[s].label}
            </span>
            <strong>{summary.data[s]}</strong>
          </Link>
        ))}
      </div>
      {flaky.data && flaky.data.content.length > 0 && (
        <>
          <div className="row spread">
            <h3>Most flaky</h3>
            <Link to={`/p/${slug}/tests?state=FLAKY`} className="small">
              All flaky tests →
            </Link>
          </div>
          <table>
            <tbody>
              {flaky.data.content.map((t) => (
                <tr key={t.id}>
                  <td>
                    <Link to={`/p/${slug}/tests/${t.id}`}>
                      <span className="muted">{t.suite} ›</span> {t.name}
                    </Link>
                  </td>
                  <td>
                    <StateBadge state={t.state} />
                  </td>
                  <td>{Number(t.score).toFixed(2)}</td>
                  <td>
                    <OutcomeStrip outcomes={t.windowOutcomes} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </section>
  )
}

function NewlyFlaky({ slug }) {
  const newly = useNewlyFlaky(slug)
  if (!newly.data || newly.data.length === 0) return null
  return (
    <section className="card">
      <h2>Newly flaky this week</h2>
      <table>
        <tbody>
          {newly.data.map((t) => (
            <tr key={t.id}>
              <td>
                <Link to={`/p/${slug}/tests/${t.id}`}>
                  <span className="muted">{t.suite} ›</span> {t.name}
                </Link>
              </td>
              <td className="muted small">was {t.fromState ? STATE_INFO[t.fromState].label.toLowerCase() : 'new'}</td>
              <td className="muted small">{formatDateTime(t.changedAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}

export function ProjectPage() {
  const { slug = '' } = useParams()
  const me = useMe()
  const project = useProject(slug)
  const runs = useRuns(slug, 0, '', 10)
  const uploads = useUploads(slug)
  const [days, setDays] = useState(30)

  if (project.isLoading) return <Skeleton height={320} />
  if (project.error) return <p className="error">{project.error.message}</p>
  const p = project.data
  const pending = uploads.data?.filter((u) => u.status === 'QUEUED' || u.status === 'PROCESSING') ?? []
  const failedUploads = uploads.data?.filter((u) => u.status === 'FAILED').slice(0, 3) ?? []
  const isMember = p.role === 'OWNER' || p.role === 'MEMBER' || (p.role === 'VIEWER' && !p.publicDemo)

  return (
    <>
      {p.publicDemo && (
        <p className="notice demo-banner">
          👀 This is the <strong>public demo</strong>: a fictional shop with 60 days of generated CI runs, read-only.{' '}
          {me.data ? (
            <Link to="/projects">Go to your projects →</Link>
          ) : (
            <Link to="/signup">Create a free account to track your own tests →</Link>
          )}
        </p>
      )}

      <div className="row spread">
        <h1>{p.name}</h1>
        <nav className="row" aria-label="Project">
          <Link to={`/p/${slug}/tests`} className="button ghost">
            Tests
          </Link>
          <Link to={`/p/${slug}/runs`} className="button ghost">
            Runs
          </Link>
          <Link to={`/p/${slug}/signatures`} className="button ghost">
            Failures
          </Link>
          {isMember && (
            <Link to={`/p/${slug}/setup`} className="button ghost">
              Setup
            </Link>
          )}
          {p.role === 'OWNER' && (
            <Link to={`/p/${slug}/settings`} className="button ghost">
              Settings
            </Link>
          )}
        </nav>
      </div>

      {pending.length > 0 && (
        <p className="notice">
          ⏳ {pending.length} upload{pending.length > 1 ? 's are' : ' is'} being processed…
        </p>
      )}
      {failedUploads.map((u) => (
        <p key={u.jobId} className="notice error">
          ⚠ Upload from {formatDateTime(u.createdAt)} failed: {u.error}
        </p>
      ))}

      {runs.data?.totalElements > 0 && (
        <div className="row chips" role="group" aria-label="Period">
          {PERIODS.map((d) => (
            <button key={d} className={`chip ${days === d ? 'active' : ''}`} onClick={() => setDays(d)}>
              Last {d} days
            </button>
          ))}
        </div>
      )}
      {runs.data?.totalElements > 0 && <Kpis slug={slug} days={days} />}
      {runs.data?.totalElements > 0 && <Trends slug={slug} days={days} />}

      <TestHealth slug={slug} />
      <NewlyFlaky slug={slug} />

      <section className="card">
        <div className="row spread">
          <h2>Recent runs</h2>
          {runs.data && runs.data.totalElements > 10 && (
            <Link to={`/p/${slug}/runs`}>See all {runs.data.totalElements} →</Link>
          )}
        </div>
        {runs.isLoading && <Skeleton height={120} />}
        {runs.data?.totalElements === 0 && (
          <div className="empty">
            <p>
              <strong>No test runs yet.</strong>
            </p>
            <p className="muted">
              Create an API key, then send your first JUnit report from CI (or with one curl command). The{' '}
              <Link to={`/p/${slug}/setup`}>Setup guide</Link> has copy-paste snippets.
            </p>
          </div>
        )}
        {runs.data && runs.data.content.length > 0 && <RunsTable slug={slug} runs={runs.data.content} />}
      </section>

      <p className="muted small">
        Default branch: <code>{p.defaultBranch}</code> · Your role: <span className="badge">{p.role}</span>
      </p>
    </>
  )
}
