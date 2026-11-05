import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { useSignatures } from '../../api/hooks'
import { formatDateTime } from '../../lib/format'

export function SignaturesPage() {
  const { slug = '' } = useParams()
  const [page, setPage] = useState(0)
  const signatures = useSignatures(slug, page)
  const totalPages = signatures.data ? Math.max(1, Math.ceil(signatures.data.totalElements / 20)) : 1

  return (
    <>
      <p>
        <Link to={`/p/${slug}`}>← Project</Link>
      </p>
      <h1>Failure groups</h1>
      <p className="muted">
        Failures with the same root cause are grouped together: numbers, ids, durations and quoted values are ignored,
        so “Timeout 5000ms waiting for #pay-1” and “Timeout 3000ms waiting for #pay-7” count as one problem.
      </p>

      {signatures.isLoading && <p className="muted">Loading…</p>}
      {signatures.error && <p className="error">{signatures.error.message}</p>}
      {signatures.data?.totalElements === 0 && <p className="muted">No failures recorded yet 🎉</p>}

      {signatures.data?.content.map((s) => (
        <section key={s.id} className="card">
          <div className="row spread">
            <strong>{s.exceptionType}</strong>
            <span>
              <span className="badge">{s.occurrences}× failed</span>
              <span className="badge">
                {s.affectedTests} test{s.affectedTests === 1 ? '' : 's'}
              </span>
            </span>
          </div>
          <p>
            <code>{s.normalizedMessage}</code>
          </p>
          <p className="muted small">
            First seen {formatDateTime(s.firstSeen)} · last seen {formatDateTime(s.lastSeen)}
          </p>
          {s.sampleStack && (
            <details>
              <summary>Example stack trace</summary>
              <pre>{s.sampleStack}</pre>
            </details>
          )}
        </section>
      ))}

      {signatures.data && signatures.data.totalElements > 20 && (
        <div className="row spread pager">
          <button className="button ghost" disabled={page === 0} onClick={() => setPage(page - 1)}>
            ← Previous
          </button>
          <span className="muted">
            Page {page + 1} of {totalPages}
          </span>
          <button className="button ghost" disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)}>
            Next →
          </button>
        </div>
      )}
    </>
  )
}
