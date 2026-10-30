import { Link, useParams } from 'react-router'
import { useProject, useRuns, useUploads } from '../../api/hooks'
import { formatDateTime } from '../../lib/format'
import { RunsTable } from '../runs/RunsTable'

export function ProjectPage() {
  const { slug = '' } = useParams()
  const project = useProject(slug)
  const runs = useRuns(slug, 0, '', 10)
  const uploads = useUploads(slug)

  if (project.isLoading) return <p className="muted">Loading…</p>
  if (project.error) return <p className="error">{project.error.message}</p>
  const p = project.data!
  const pending = uploads.data?.filter((u) => u.status === 'QUEUED' || u.status === 'PROCESSING') ?? []
  const failedUploads = uploads.data?.filter((u) => u.status === 'FAILED').slice(0, 3) ?? []

  return (
    <>
      <div className="row spread">
        <h1>{p.name}</h1>
        <div className="row">
          <Link to={`/p/${slug}/runs`} className="button ghost">
            Runs
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
