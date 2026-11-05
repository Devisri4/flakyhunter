import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { useLogin, useSignup } from '../../api/hooks'
import { PasswordInput } from '../../components/PasswordInput'

function useAfterLogin() {
  const navigate = useNavigate()
  const location = useLocation()
  const from = location.state?.from ?? '/projects'
  return () => navigate(from, { replace: true })
}

/** The coloured half of the login/sign-up card. */
function BrandPanel({ title }) {
  return (
    <aside className="auth-brand">
      <img src="/logo.svg" alt="" width={48} height={48} />
      <h2>{title}</h2>
      <ul>
        <li>🔎 Every CI run's tests tracked across runs</li>
        <li>↯ Flaky tests told apart from really broken ones</li>
        <li>🧊 Quarantine a flaky test so it stops blocking builds</li>
      </ul>
      <p className="small">
        Just looking? <Link to="/p/demo">Open the live demo →</Link>
      </p>
    </aside>
  )
}

export function LoginPage() {
  const login = useLogin()
  const done = useAfterLogin()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  function submit(e) {
    e.preventDefault()
    login.mutate({ email, password }, { onSuccess: done })
  }

  return (
    <div className="auth-shell">
      <BrandPanel title="Welcome back" />
      <form className="auth-form" onSubmit={submit}>
        <h1>Log in</h1>
        <label>
          Email
          <input type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <div className="field">
          <label htmlFor="login-password">Password</label>
          <PasswordInput
            id="login-password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </div>
        {login.error && <p className="error">{login.error.message}</p>}
        <button className="button" disabled={login.isPending}>
          {login.isPending ? 'Logging in…' : 'Log in'}
        </button>
        <p className="muted">
          No account yet? <Link to="/signup">Sign up</Link>
        </p>
      </form>
    </div>
  )
}

export function SignupPage() {
  const signup = useSignup()
  const done = useAfterLogin()
  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  function submit(e) {
    e.preventDefault()
    signup.mutate({ displayName, email, password }, { onSuccess: done })
  }

  return (
    <div className="auth-shell">
      <BrandPanel title="Stop flaky tests from blocking your builds" />
      <form className="auth-form" onSubmit={submit}>
        <h1>Create your account</h1>
        <label>
          Your name
          <input required maxLength={60} value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
        </label>
        <label>
          Email
          <input type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <div className="field">
          <label htmlFor="signup-password">
            Password <span className="muted small">(at least 8 characters)</span>
          </label>
          <PasswordInput
            id="signup-password"
            autoComplete="new-password"
            required
            minLength={8}
            maxLength={72}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </div>
        {signup.error && <p className="error">{signup.error.message}</p>}
        <button className="button" disabled={signup.isPending}>
          {signup.isPending ? 'Creating…' : 'Sign up'}
        </button>
        <p className="muted">
          Already have an account? <Link to="/login">Log in</Link>
        </p>
      </form>
    </div>
  )
}
