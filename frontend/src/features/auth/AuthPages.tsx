import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { useLogin, useSignup } from '../../api/hooks'

function useAfterLogin() {
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/projects'
  return () => navigate(from, { replace: true })
}

export function LoginPage() {
  const login = useLogin()
  const done = useAfterLogin()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  function submit(e: FormEvent) {
    e.preventDefault()
    login.mutate({ email, password }, { onSuccess: done })
  }

  return (
    <form className="card narrow" onSubmit={submit}>
      <h1>Log in</h1>
      <label>
        Email
        <input type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
      </label>
      <label>
        Password
        <input
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
      </label>
      {login.error && <p className="error">{login.error.message}</p>}
      <button className="button" disabled={login.isPending}>
        {login.isPending ? 'Logging in…' : 'Log in'}
      </button>
      <p className="muted">
        No account yet? <Link to="/signup">Sign up</Link>
      </p>
    </form>
  )
}

export function SignupPage() {
  const signup = useSignup()
  const done = useAfterLogin()
  const [displayName, setDisplayName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  function submit(e: FormEvent) {
    e.preventDefault()
    signup.mutate({ displayName, email, password }, { onSuccess: done })
  }

  return (
    <form className="card narrow" onSubmit={submit}>
      <h1>Create your account</h1>
      <label>
        Your name
        <input required maxLength={60} value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
      </label>
      <label>
        Email
        <input type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
      </label>
      <label>
        Password <span className="muted">(at least 8 characters)</span>
        <input
          type="password"
          autoComplete="new-password"
          required
          minLength={8}
          maxLength={72}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
      </label>
      {signup.error && <p className="error">{signup.error.message}</p>}
      <button className="button" disabled={signup.isPending}>
        {signup.isPending ? 'Creating…' : 'Sign up'}
      </button>
      <p className="muted">
        Already have an account? <Link to="/login">Log in</Link>
      </p>
    </form>
  )
}
