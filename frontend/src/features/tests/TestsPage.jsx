import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { useTests } from '../../api/hooks'
import { OutcomeStrip, StateBadge, STATE_INFO } from '../../components/TestBadges'
import { formatDateTime } from '../../lib/format'

const STATES = ['FLAKY', 'BROKEN', 'SUSPECT', 'STABLE', 'NEW']
const PAGE_SIZE = 25

export function TestsPage() {
  const { slug = '' } = useParams()
  const [params, setParams] = useSearchParams()
  const state = params.get('state') ?? ''
  const q = params.get('q') ?? ''
  const sort = params.get('sort') ?? 'score'
  const page = Number(params.get('page') ?? 0)
  const quarantinedParam = params.get('quarantined')
  const quarantined = quarantinedParam === null ? undefined : quarantinedParam === 'true'
  const [search, setSearch] = useState(q)
  const tests = useTests(slug, { state, quarantined, q, sort, page, size: PAGE_SIZE })
  const totalPages = tests.data ? Math.max(1, Math.ceil(tests.data.totalElements / PAGE_SIZE)) : 1

  function update(changes) {
    const next = new URLSearchParams(params)
    Object.entries(changes).forEach(([k, v]) => (v ? next.set(k, v) : next.delete(k)))
    if (!('page' in changes)) next.delete('page')
    setParams(next)
  }

  function submit(e) {
    e.preventDefault()
    update({ q: search })
  }

  return (
    <>
      <p>
        <Link to={`/p/${slug}`}>← Project</Link>
      </p>
      <h1>Tests</h1>

      <div className="row chips" role="group" aria-label="Filter by state">
        <button className={`chip ${state === '' ? 'active' : ''}`} onClick={() => update({ state: '' })}>
          All
        </button>
        {STATES.map((s) => (
          <button key={s} className={`chip ${state === s ? 'active' : ''}`} onClick={() => update({ state: s })}>
            {STATE_INFO[s].icon} {STATE_INFO[s].label}
          </button>
        ))}
        <button
          className={`chip ${quarantined === true ? 'active' : ''}`}
          aria-pressed={quarantined === true}
          onClick={() => update({ quarantined: quarantined === true ? '' : 'true' })}
        >
          🧊 Quarantined
        </button>
      </div>

      <form className="row" onSubmit={submit}>
        <input placeholder="Search by class or test name" value={search} onChange={(e) => setSearch(e.target.value)} />
        <select value={sort} onChange={(e) => update({ sort: e.target.value })} aria-label="Sort by">
          <option value="score">Most flaky first</option>
          <option value="failStreak">Longest fail streak</option>
          <option value="lastSeen">Recently seen</option>
          <option value="name">Name</option>
        </select>
        <button className="button">Search</button>
      </form>

      {tests.isLoading && <p className="muted">Loading…</p>}
      {tests.error && <p className="error">{tests.error.message}</p>}
      {tests.data?.totalElements === 0 && <p className="muted">No tests match.</p>}

      {tests.data && tests.data.content.length > 0 && (
        <section className="card">
          <table>
            <thead>
              <tr>
                <th>Test</th>
                <th>State</th>
                <th>Score</th>
                <th>Recent runs (oldest → newest)</th>
                <th>Last seen</th>
              </tr>
            </thead>
            <tbody>
              {tests.data.content.map((t) => (
                <tr key={t.id}>
                  <td>
                    <Link to={`/p/${slug}/tests/${t.id}`}>
                      <span className="muted">{t.suite} ›</span> {t.name}
                    </Link>
                  </td>
                  <td>
                    <StateBadge state={t.state} />
                    {t.quarantined && (
                      <span className="state state-quarantined" title="Quarantined: CI skips it">
                        🧊
                      </span>
                    )}
                  </td>
                  <td title={`${t.flipCount} flips, ${t.retryPassCount} retries in ${t.runsInWindow} runs`}>
                    {Number(t.score).toFixed(2)}
                  </td>
                  <td>
                    <OutcomeStrip outcomes={t.windowOutcomes} />
                  </td>
                  <td>{formatDateTime(t.lastSeen)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="row spread pager">
            <button className="button ghost" disabled={page === 0} onClick={() => update({ page: String(page - 1) })}>
              ← Previous
            </button>
            <span className="muted">
              Page {page + 1} of {totalPages} · {tests.data.totalElements} tests
            </span>
            <button
              className="button ghost"
              disabled={page + 1 >= totalPages}
              onClick={() => update({ page: String(page + 1) })}
            >
              Next →
            </button>
          </div>
        </section>
      )}

      <p className="muted small">
        Score 0–1 combines retries that passed, pass↔fail flips and commits that both passed and failed. ≥ 0.30 is
        flaky; three straight failures is broken.
      </p>
    </>
  )
}
