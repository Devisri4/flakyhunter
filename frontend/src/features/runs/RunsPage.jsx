import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { useBranches, useRuns } from '../../api/hooks'
import { RunsTable } from './RunsTable'

const PAGE_SIZE = 20

export function RunsPage() {
  const { slug = '' } = useParams()
  const [page, setPage] = useState(0)
  const [branch, setBranch] = useState('')
  const runs = useRuns(slug, page, branch, PAGE_SIZE)
  const branches = useBranches(slug)
  const totalPages = runs.data ? Math.max(1, Math.ceil(runs.data.totalElements / PAGE_SIZE)) : 1

  return (
    <>
      <p>
        <Link to={`/p/${slug}`}>← Project</Link>
      </p>
      <div className="row spread">
        <h1>Runs</h1>
        <label className="inline">
          Branch
          <select
            value={branch}
            onChange={(e) => {
              setBranch(e.target.value)
              setPage(0)
            }}
          >
            <option value="">All branches</option>
            {branches.data?.map((b) => (
              <option key={b}>{b}</option>
            ))}
          </select>
        </label>
      </div>

      {runs.isLoading && <p className="muted">Loading…</p>}
      {runs.error && <p className="error">{runs.error.message}</p>}
      {runs.data?.totalElements === 0 && (
        <p className="muted">
          No runs yet — see <Link to={`/p/${slug}/setup`}>Setup</Link> to send your first report.
        </p>
      )}
      {runs.data && runs.data.content.length > 0 && (
        <section className="card">
          <RunsTable slug={slug} runs={runs.data.content} />
          <div className="row spread pager">
            <button className="button ghost" disabled={page === 0} onClick={() => setPage(page - 1)}>
              ← Newer
            </button>
            <span className="muted">
              Page {page + 1} of {totalPages}
            </span>
            <button className="button ghost" disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)}>
              Older →
            </button>
          </div>
        </section>
      )}
    </>
  )
}
