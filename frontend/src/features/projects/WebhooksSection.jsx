import { useState } from 'react'
import { useCreateWebhook, useDeleteWebhook, useTestWebhook, useToggleWebhook, useWebhooks } from '../../api/hooks'
import { formatDateTime } from '../../lib/format'

const KINDS = [
  { value: 'SLACK', label: 'Slack', hint: 'an Incoming Webhook URL (https://hooks.slack.com/services/…)' },
  { value: 'DISCORD', label: 'Discord', hint: 'a channel webhook URL (https://discord.com/api/webhooks/…)' },
  { value: 'GENERIC', label: 'Generic JSON', hint: 'any HTTPS endpoint; requests are signed with HMAC-SHA256' },
]

function statusTone(status) {
  if (!status) return 'muted'
  return /^2\d\d$/.test(status) ? 'up' : 'down'
}

/** Alerts when a test becomes flaky (owner only). */
export function WebhooksSection({ slug }) {
  const hooks = useWebhooks(slug)
  const create = useCreateWebhook(slug)
  const toggle = useToggleWebhook(slug)
  const test = useTestWebhook(slug)
  const remove = useDeleteWebhook(slug)
  const [kind, setKind] = useState('SLACK')
  const [url, setUrl] = useState('')
  const [secret, setSecret] = useState(null)

  function submit(e) {
    e.preventDefault()
    create.mutate(
      { kind, url },
      {
        onSuccess: (result) => {
          setUrl('')
          if (result.webhook.kind === 'GENERIC') setSecret(result.secret)
        },
      },
    )
  }

  const error = create.error ?? toggle.error ?? test.error ?? remove.error

  return (
    <section className="card">
      <h2>Alerts (webhooks)</h2>
      <p className="muted">
        Get a message in Slack, Discord or your own service when tests <strong>become flaky</strong> after a run.
      </p>

      {secret && (
        <div className="notice">
          <strong>Signing secret — shown only now.</strong>
          <code className="key">{secret}</code>
          <p className="muted small">
            Each request carries <code>X-FlakyHunter-Signature: sha256=&lt;hex&gt;</code>, the HMAC-SHA256 of the
            body with this secret. Compare it to reject fake calls.
          </p>
          <button className="button ghost" onClick={() => setSecret(null)}>
            I saved it
          </button>
        </div>
      )}

      <form className="row" onSubmit={submit}>
        <select value={kind} onChange={(e) => setKind(e.target.value)} aria-label="Service">
          {KINDS.map((k) => (
            <option key={k.value} value={k.value}>
              {k.label}
            </option>
          ))}
        </select>
        <input
          type="url"
          required
          placeholder="https://…"
          value={url}
          onChange={(e) => setUrl(e.target.value)}
          aria-label="Webhook URL"
        />
        <button className="button" disabled={create.isPending}>
          Add
        </button>
      </form>
      <p className="muted small">{KINDS.find((k) => k.value === kind).hint}. Only public https:// addresses are allowed.</p>
      {error && <p className="error">{error.message}</p>}

      {hooks.data?.length > 0 && (
        <table>
          <tbody>
            {hooks.data.map((h) => (
              <tr key={h.id} className={h.enabled ? undefined : 'revoked'}>
                <td>
                  <span className="badge">{KINDS.find((k) => k.value === h.kind)?.label ?? h.kind}</span>
                </td>
                <td className="clip" title={h.url}>
                  {h.url}
                </td>
                <td className={`small ${statusTone(h.lastStatus)}`}>
                  {h.lastStatus ? `${h.lastStatus} · ${formatDateTime(h.lastDeliveryAt)}` : 'not sent yet'}
                </td>
                <td className="row">
                  <button className="button ghost" onClick={() => test.mutate(h.id)} disabled={test.isPending}>
                    Send test
                  </button>
                  <button className="button ghost" onClick={() => toggle.mutate({ id: h.id, enabled: !h.enabled })}>
                    {h.enabled ? 'Pause' : 'Resume'}
                  </button>
                  <button
                    className="button danger"
                    onClick={() => confirm('Delete this webhook?') && remove.mutate(h.id)}
                  >
                    Delete
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
