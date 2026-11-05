import { useState } from 'react'
import { useLiftQuarantine, useQuarantineTest } from '../../api/hooks'
import { formatDateTime } from '../../lib/format'

const LENGTHS = [3, 7, 14, 30]

/**
 * Quarantine status of one test. Members can quarantine (with a reason and an expiry) or lift it; viewers and
 * demo visitors only see the status. Quarantined tests are published to CI via /api/v1/quarantine.
 */
export function QuarantinePanel({ slug, testId, active, history, canManage }) {
  const quarantine = useQuarantineTest(slug, testId)
  const lift = useLiftQuarantine(slug, testId)
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState('')
  const [days, setDays] = useState(14)

  function submit(e) {
    e.preventDefault()
    quarantine.mutate({ reason, days }, { onSuccess: () => setOpen(false) })
  }

  const error = quarantine.error ?? lift.error

  return (
    <section className={`card ${active ? 'quarantined' : ''}`}>
      <div className="row spread">
        <h2>Quarantine</h2>
        {canManage && active && (
          <button className="button ghost" onClick={() => lift.mutate()} disabled={lift.isPending}>
            Lift quarantine
          </button>
        )}
        {canManage && !active && !open && (
          <button className="button" onClick={() => setOpen(true)}>
            Quarantine this test
          </button>
        )}
      </div>

      {active ? (
        <p>
          <strong>🧊 Quarantined</strong> until {formatDateTime(active.expiresAt)} — “{active.reason}”
          {active.createdBy && <span className="muted"> · by {active.createdBy}</span>}
          <br />
          <span className="muted small">
            CI tools that use FlakyHunter (the JUnit 5 extension, the Playwright pattern on the Setup page) skip it
            until then. It lifts itself when it expires.
          </span>
        </p>
      ) : (
        !open && (
          <p className="muted">
            Not quarantined.{' '}
            {canManage
              ? 'Quarantine a flaky test to stop it blocking builds while someone fixes it.'
              : 'Project members can quarantine flaky tests.'}
          </p>
        )
      )}

      {open && (
        <form className="quarantine-form" onSubmit={submit}>
          <label>
            Reason
            <input
              required
              maxLength={500}
              placeholder="e.g. times out on CI, see ticket SHOP-123"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </label>
          <label>
            For
            <select value={days} onChange={(e) => setDays(Number(e.target.value))}>
              {LENGTHS.map((d) => (
                <option key={d} value={d}>
                  {d} days
                </option>
              ))}
            </select>
          </label>
          <div className="row">
            <button className="button" disabled={quarantine.isPending}>
              Quarantine
            </button>
            <button type="button" className="button ghost" onClick={() => setOpen(false)}>
              Cancel
            </button>
          </div>
        </form>
      )}
      {error && <p className="error">{error.message}</p>}

      {history.length > 0 && (
        <details>
          <summary className="muted small">History ({history.length})</summary>
          <ul className="timeline">
            {history.map((q) => (
              <li key={q.id}>
                <span className="muted small">{formatDateTime(q.createdAt)}</span>
                <div>
                  “{q.reason}” {q.createdBy && <span className="muted">by {q.createdBy}</span>} ·{' '}
                  {q.liftedAt
                    ? `lifted ${formatDateTime(q.liftedAt)} (${q.liftReason === 'expired' ? 'expired' : `by ${q.liftedBy ?? 'a member'}`})`
                    : `active until ${formatDateTime(q.expiresAt)}`}
                </div>
              </li>
            ))}
          </ul>
        </details>
      )}
    </section>
  )
}
