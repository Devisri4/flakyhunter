/**
 * Tiny fetch wrapper for the FlakyHunter API.
 * - Login lives in HttpOnly cookies, so requests just send cookies (same origin via the Vercel /api proxy).
 * - State-changing requests echo the XSRF-TOKEN cookie in the X-XSRF-TOKEN header (CSRF protection).
 * - A 401 triggers one refresh of the session, then the request is retried once.
 */

export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

type Options = { method?: 'GET' | 'POST' | 'PATCH' | 'DELETE'; body?: unknown }

/** Endpoints where a 401 must not trigger a refresh-and-retry. */
const NO_RETRY = ['/api/auth/refresh', '/api/auth/login', '/api/auth/signup', '/api/auth/logout']

export function readCookie(name: string, cookies: string = document.cookie): string | null {
  for (const part of cookies.split(';')) {
    const [key, ...rest] = part.trim().split('=')
    if (key === name) return decodeURIComponent(rest.join('='))
  }
  return null
}

async function csrfHeaders(): Promise<Record<string, string>> {
  if (!readCookie('XSRF-TOKEN')) {
    await fetch('/api/auth/csrf', { credentials: 'same-origin' })
  }
  const token = readCookie('XSRF-TOKEN')
  return token ? { 'X-XSRF-TOKEN': token } : {}
}

let refreshing: Promise<boolean> | null = null

/** Several requests may get 401 at once: they all share one refresh call. */
function refreshSession(): Promise<boolean> {
  refreshing ??= csrfHeaders()
    .then((headers) => fetch('/api/auth/refresh', { method: 'POST', headers, credentials: 'same-origin' }))
    .then((res) => res.ok)
    .catch(() => false)
    .finally(() => {
      refreshing = null
    })
  return refreshing
}

export async function problemMessage(res: Response): Promise<string> {
  try {
    const problem = await res.json()
    return problem.detail ?? problem.title ?? `Request failed (${res.status})`
  } catch {
    return `Request failed (${res.status})`
  }
}

export async function api<T>(path: string, options: Options = {}, retry = true): Promise<T> {
  const method = options.method ?? 'GET'
  const headers: Record<string, string> = method === 'GET' ? {} : await csrfHeaders()
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'

  const res = await fetch(path, {
    method,
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    credentials: 'same-origin',
  })

  if (res.status === 401 && retry && !NO_RETRY.includes(path) && (await refreshSession())) {
    return api<T>(path, options, false)
  }
  if (!res.ok) throw new ApiError(res.status, await problemMessage(res))
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}
