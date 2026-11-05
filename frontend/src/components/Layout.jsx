import { Link, Navigate, Outlet, useLocation, useNavigate, useParams } from 'react-router'
import { useLogout, useMe, useProject } from '../api/hooks'
import { Skeleton } from './Skeleton'

export function Layout() {
  const me = useMe()
  const logout = useLogout()
  const navigate = useNavigate()

  return (
    <>
      <header className="topbar">
        <Link to="/" className="brand-link">
          <img src="/logo.svg" alt="" width={28} height={28} />
          <span>
            Flaky<span className="accent">Hunter</span>
          </span>
        </Link>
        <nav>
          {me.data ? (
            <>
              <Link to="/projects">Projects</Link>
              <span className="muted">{me.data.displayName}</span>
              <button
                className="button ghost"
                onClick={() => logout.mutate(undefined, { onSettled: () => navigate('/') })}
              >
                Log out
              </button>
            </>
          ) : (
            !me.isLoading && (
              <>
                <Link to="/p/demo">Demo</Link>
                <Link to="/login">Log in</Link>
                <Link to="/signup" className="button">
                  Sign up
                </Link>
              </>
            )
          )}
        </nav>
      </header>
      <main>
        <Outlet />
      </main>
    </>
  )
}

/**
 * Project pages work without a login when the project is the public demo. For any other project, a visitor
 * who isn't logged in is sent to /login and comes back afterwards.
 */
export function ProjectGate() {
  const { slug = '' } = useParams()
  const me = useMe()
  const project = useProject(slug)
  const location = useLocation()
  if (me.isLoading || project.isLoading) return <Skeleton height={320} />
  if (project.error?.status === 401 && !me.data) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }
  return <Outlet />
}

/** Pages inside need a logged-in user; others are sent to /login and come back afterwards. */
export function RequireAuth() {
  const me = useMe()
  const location = useLocation()
  if (me.isLoading) return <p className="muted">Loading…</p>
  if (!me.data) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return <Outlet />
}
