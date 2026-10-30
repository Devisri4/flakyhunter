import { Link } from 'react-router'
import type { RunSummary } from '../../api/hooks'
import { formatDateTime, formatDuration, passRate, shortSha } from '../../lib/format'

/** Totals as small coloured counts: ✓ passed, ✗ failed, ↻ flaky (passed on retry), ⤼ skipped. */
export function RunCounts({ run }: { run: RunSummary }) {
  return (
    <span className="counts">
      <span className="up" title="passed">✓ {run.passed}</span>
      {run.failed > 0 && <span className="down" title="failed">✗ {run.failed}</span>}
      {run.flaky > 0 && <span className="flaky" title="passed on retry">↻ {run.flaky}</span>}
      {run.skipped > 0 && <span className="muted" title="skipped">⤼ {run.skipped}</span>}
    </span>
  )
}

export function RunsTable({ slug, runs }: { slug: string; runs: RunSummary[] }) {
  return (
    <table>
      <thead>
        <tr>
          <th>Run</th>
          <th>When</th>
          <th>Branch</th>
          <th>Commit</th>
          <th>Results</th>
          <th>Pass rate</th>
          <th>Duration</th>
        </tr>
      </thead>
      <tbody>
        {runs.map((run) => {
          const rate = passRate(run)
          return (
            <tr key={run.id}>
              <td>
                <Link to={`/p/${slug}/runs/${run.id}`}>#{run.id}</Link>
              </td>
              <td>{formatDateTime(run.triggeredAt)}</td>
              <td>{run.branch ?? '—'}</td>
              <td>
                <code>{shortSha(run.commitSha)}</code>
              </td>
              <td>
                <RunCounts run={run} />
              </td>
              <td className={run.failed > 0 ? 'down' : 'up'}>{rate === null ? '—' : `${rate}%`}</td>
              <td>{formatDuration(run.durationMs)}</td>
            </tr>
          )
        })}
      </tbody>
    </table>
  )
}
