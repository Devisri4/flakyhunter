import { Link, Navigate, Outlet, useLocation, useNavigate } from 'react-router'
import { useLogout, useMe } from '../api/hooks'

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

/** Pages inside need a logged-in user; others are sent to /login and come back afterwards. */
export function RequireAuth() {
  const me = useMe()
  const location = useLocation()
  if (me.isLoading) return <p className="muted">Loading…</p>
  if (!me.data) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return <Outlet />
}
