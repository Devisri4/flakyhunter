import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import {
  useAddMember,
  useApiKeys,
  useChangeRole,
  useCreateApiKey,
  useDeleteProject,
  useMe,
  useMembers,
  useProject,
  useRemoveMember,
  useRevokeApiKey,
  type Role,
} from '../../api/hooks'

const ROLES: Role[] = ['OWNER', 'MEMBER', 'VIEWER']

function formatDate(iso: string | null) {
  return iso ? new Date(iso).toLocaleString() : '—'
}

export function SettingsPage() {
  const { slug = '' } = useParams()
  const project = useProject(slug)

  if (project.isLoading) return <p className="muted">Loading…</p>
  if (project.error) return <p className="error">{project.error.message}</p>
  if (project.data!.role !== 'OWNER') return <p className="error">Only project owners can open settings.</p>

  return (
    <>
      <p>
        <Link to={`/p/${slug}`}>← {project.data!.name}</Link>
      </p>
      <h1>Settings</h1>
      <ApiKeysSection slug={slug} />
      <MembersSection slug={slug} />
      <DangerZone slug={slug} name={project.data!.name} />
    </>
  )
}

function ApiKeysSection({ slug }: { slug: string }) {
  const keys = useApiKeys(slug)
  const create = useCreateApiKey(slug)
  const revoke = useRevokeApiKey(slug)
  const [name, setName] = useState('')
  const [newKey, setNewKey] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

  function submit(e: FormEvent) {
    e.preventDefault()
    create.mutate(name, {
      onSuccess: (result) => {
        setNewKey(result.key)
        setCopied(false)
        setName('')
      },
    })
  }

  async function copy() {
    if (!newKey) return
    await navigator.clipboard.writeText(newKey)
    setCopied(true)
  }

  return (
    <section className="card">
      <h2>API keys</h2>
      <p className="muted">Your CI pipeline uses an API key to upload test results to this project.</p>

      {newKey && (
        <div className="notice">
          <strong>Copy your new key now — it will not be shown again.</strong>
          <div className="row">
            <code className="key">{newKey}</code>
            <button className="button" onClick={copy}>
              {copied ? 'Copied ✓' : 'Copy'}
            </button>
          </div>
          <p className="muted">Store it as a secret in your CI (for example a GitHub Actions secret). Never commit it.</p>
          <button className="button ghost" onClick={() => setNewKey(null)}>
            I saved it
          </button>
        </div>
      )}

      <form className="row" onSubmit={submit}>
        <input
          placeholder="Key name, e.g. GitHub Actions"
          required
          maxLength={60}
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        <button className="button" disabled={create.isPending}>
          Create key
        </button>
      </form>
      {create.error && <p className="error">{create.error.message}</p>}

      <table>
        <thead>
          <tr>
            <th>Name</th>
            <th>Key</th>
            <th>Created</th>
            <th>Last used</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {keys.data?.map((k) => (
            <tr key={k.id} className={k.revokedAt ? 'revoked' : undefined}>
              <td>{k.name}</td>
              <td>
                <code>{k.prefix}…</code>
              </td>
              <td>{formatDate(k.createdAt)}</td>
              <td>{formatDate(k.lastUsedAt)}</td>
              <td>
                {k.revokedAt ? (
                  <span className="badge">revoked</span>
                ) : (
                  <button
                    className="button danger"
                    onClick={() => confirm(`Revoke "${k.name}"? CI using it will stop working.`) && revoke.mutate(k.id)}
                  >
                    Revoke
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {keys.data?.length === 0 && <p className="muted">No keys yet.</p>}
    </section>
  )
}

function MembersSection({ slug }: { slug: string }) {
  const me = useMe()
  const members = useMembers(slug)
  const add = useAddMember(slug)
  const changeRole = useChangeRole(slug)
  const remove = useRemoveMember(slug)
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<Role>('MEMBER')

  function submit(e: FormEvent) {
    e.preventDefault()
    add.mutate({ email, role }, { onSuccess: () => setEmail('') })
  }

  const error = add.error ?? changeRole.error ?? remove.error

  return (
    <section className="card">
      <h2>Members</h2>
      <p className="muted">Viewers can see results, members can also quarantine tests, owners manage everything.</p>
      <form className="row" onSubmit={submit}>
        <input
          type="email"
          placeholder="Their FlakyHunter email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <select value={role} onChange={(e) => setRole(e.target.value as Role)}>
          {ROLES.map((r) => (
            <option key={r}>{r}</option>
          ))}
        </select>
        <button className="button" disabled={add.isPending}>
          Add
        </button>
      </form>
      {error && <p className="error">{error.message}</p>}

      <table>
        <tbody>
          {members.data?.map((m) => (
            <tr key={m.userId}>
              <td>
                {m.displayName} {m.userId === me.data?.id && <span className="muted">(you)</span>}
              </td>
              <td className="muted">{m.email}</td>
              <td>
                <select
                  value={m.role}
                  onChange={(e) => changeRole.mutate({ userId: m.userId, role: e.target.value as Role })}
                >
                  {ROLES.map((r) => (
                    <option key={r}>{r}</option>
                  ))}
                </select>
              </td>
              <td>
                <button
                  className="button danger"
                  onClick={() => confirm(`Remove ${m.displayName} from this project?`) && remove.mutate(m.userId)}
                >
                  Remove
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}

function DangerZone({ slug, name }: { slug: string; name: string }) {
  const del = useDeleteProject(slug)
  const navigate = useNavigate()

  function onDelete() {
    const typed = prompt(`This deletes "${name}" and all its data. Type the project slug (${slug}) to confirm:`)
    if (typed === slug) del.mutate(undefined, { onSuccess: () => navigate('/projects') })
  }

  return (
    <section className="card danger-zone">
      <h2>Danger zone</h2>
      <button className="button danger" onClick={onDelete} disabled={del.isPending}>
        Delete project
      </button>
      {del.error && <p className="error">{del.error.message}</p>}
    </section>
  )
}
