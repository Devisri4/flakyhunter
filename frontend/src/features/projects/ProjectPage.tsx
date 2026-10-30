import { Link, useParams } from 'react-router'
import { useProject } from '../../api/hooks'

export function ProjectPage() {
  const { slug = '' } = useParams()
  const project = useProject(slug)

  if (project.isLoading) return <p className="muted">Loading…</p>
  if (project.error) return <p className="error">{project.error.message}</p>
  const p = project.data!

  return (
    <>
      <div className="row spread">
        <h1>{p.name}</h1>
        {p.role === 'OWNER' && (
          <Link to={`/p/${slug}/settings`} className="button ghost">
            Settings
          </Link>
        )}
      </div>
      <section className="card">
        <h2>No test runs yet</h2>
        <p className="muted">
          Uploading test reports arrives in the next phase. For now, create an API key in{' '}
          {p.role === 'OWNER' ? <Link to={`/p/${slug}/settings`}>Settings</Link> : 'Settings (owners only)'} — your CI
          will use it to send results.
        </p>
        <p className="muted">
          Default branch: <code>{p.defaultBranch}</code> · Your role: <span className="badge">{p.role}</span>
        </p>
      </section>
    </>
  )
}
