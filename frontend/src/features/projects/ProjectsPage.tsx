import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { useCreateProject, useProjects } from '../../api/hooks'

export function ProjectsPage() {
  const projects = useProjects()
  const create = useCreateProject()
  const navigate = useNavigate()
  const [name, setName] = useState('')

  function submit(e: FormEvent) {
    e.preventDefault()
    create.mutate({ name }, { onSuccess: (p) => navigate(`/p/${p.slug}/settings`) })
  }

  return (
    <>
      <h1>My projects</h1>

      <form className="card row" onSubmit={submit}>
        <input
          placeholder="New project name, e.g. Acme Shop"
          required
          maxLength={80}
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        <button className="button" disabled={create.isPending}>
          {create.isPending ? 'Creating…' : 'Create project'}
        </button>
      </form>
      {create.error && <p className="error">{create.error.message}</p>}

      {projects.isLoading && <p className="muted">Loading…</p>}
      {projects.error && <p className="error">{projects.error.message}</p>}
      {projects.data?.length === 0 && (
        <p className="muted">No projects yet. Create your first one above — one project per codebase.</p>
      )}
      <ul className="list">
        {projects.data?.map((p) => (
          <li key={p.slug} className="card">
            <Link to={`/p/${p.slug}`}>
              <strong>{p.name}</strong>
            </Link>
            <span className="muted"> /{p.slug}</span>
            <span className="badge">{p.role}</span>
          </li>
        ))}
      </ul>
    </>
  )
}
