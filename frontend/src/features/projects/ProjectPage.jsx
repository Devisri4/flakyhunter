import { Link, useParams } from 'react-router'
import { useProject, useRuns, useTests, useTestSummary, useUploads } from '../../api/hooks'
import { OutcomeStrip, StateBadge, STATE_INFO } from '../../components/TestBadges'
import { formatDateTime } from '../../lib/format'
import { RunsTable } from '../runs/RunsTable'

const HEALTH_ORDER = ['FLAKY', 'BROKEN', 'SUSPECT', 'STABLE', 'NEW']

/** Counts per state (each links to the filtered test list) and the most flaky tests. */
function TestHealth({ slug }) {
  const summary = useTestSummary(slug)
  const flaky = useTests(slug, { state: 'FLAKY', size: 5 })
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
          <h3>Most flaky</h3>
          <table>
            <tbody>
              {flaky.data.content.map((t) => (
                <tr key={t.id}>
                  <td>
                    <span className="muted">{t.suite} ›</span> {t.name}
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

export function ProjectPage() {
  const { slug = '' } = useParams()
  const project = useProject(slug)
  const runs = useRuns(slug, 0, '', 10)
  const uploads = useUploads(slug)

  if (project.isLoading) return <p className="muted">Loading…</p>
  if (project.error) return <p className="error">{project.error.message}</p>
  const p = project.data
  const pending = uploads.data?.filter((u) => u.status === 'QUEUED' || u.status === 'PROCESSING') ?? []
  const failedUploads = uploads.data?.filter((u) => u.status === 'FAILED').slice(0, 3) ?? []

  return (
    <>
      <div className="row spread">
        <h1>{p.name}</h1>
        <div className="row">
          <Link to={`/p/${slug}/tests`} className="button ghost">
            Tests
          </Link>
          <Link to={`/p/${slug}/runs`} className="button ghost">
            Runs
          </Link>
          <Link to={`/p/${slug}/signatures`} className="button ghost">
            Failures
          </Link>
          <Link to={`/p/${slug}/setup`} className="button ghost">
            Setup
          </Link>
          {p.role === 'OWNER' && (
            <Link to={`/p/${slug}/settings`} className="button ghost">
              Settings
            </Link>
          )}
        </div>
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

      <TestHealth slug={slug} />

      <section className="card">
        <div className="row spread">
          <h2>Recent runs</h2>
          {runs.data && runs.data.totalElements > 10 && <Link to={`/p/${slug}/runs`}>See all {runs.data.totalElements} →</Link>}
        </div>
        {runs.isLoading && <p className="muted">Loading…</p>}
        {runs.data?.totalElements === 0 && (
          <p className="muted">
            No test runs yet. Follow the <Link to={`/p/${slug}/setup`}>Setup guide</Link> to send your first JUnit
            report from CI (or with one curl command).
          </p>
        )}
        {runs.data && runs.data.content.length > 0 && <RunsTable slug={slug} runs={runs.data.content} />}
      </section>

      <p className="muted">
        Default branch: <code>{p.defaultBranch}</code> · Your role: <span className="badge">{p.role}</span>
      </p>
    </>
  )
}
