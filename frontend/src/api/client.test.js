import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, isServerWaking, readCookie } from './client'

describe('isServerWaking', () => {
  it('treats proxy errors and "no answer" as a sleeping server', () => {
    expect(isServerWaking(new ApiError(504, 'Gateway timeout'))).toBe(true)
    expect(isServerWaking(new ApiError(502, 'Bad gateway'))).toBe(true)
    expect(isServerWaking(new TypeError('Failed to fetch'))).toBe(true)
  })

  it('does not keep retrying real errors', () => {
    expect(isServerWaking(new ApiError(401, 'Login required'))).toBe(false)
    expect(isServerWaking(new ApiError(404, 'Not found'))).toBe(false)
    expect(isServerWaking(new ApiError(500, 'Server error'))).toBe(false)
    expect(isServerWaking(new Error('something else'))).toBe(false)
  })
})

describe('readCookie', () => {
  it('finds a cookie among several', () => {
    expect(readCookie('XSRF-TOKEN', 'a=1; XSRF-TOKEN=abc%3D; b=2')).toBe('abc=')
  })

  it('returns null when missing', () => {
    expect(readCookie('XSRF-TOKEN', 'a=1')).toBeNull()
  })
})

function response(status, body) {
  return new Response(body === undefined ? null : JSON.stringify(body), { status })
}

describe('api', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('refreshes once on 401 and retries the request', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(response(401)) // GET /api/projects
      .mockResolvedValueOnce(response(200)) // GET /api/auth/csrf
      .mockResolvedValueOnce(response(200)) // POST /api/auth/refresh
      .mockResolvedValueOnce(response(200, [{ slug: 'acme' }])) // retried GET
    vi.stubGlobal('fetch', fetchMock)
    vi.stubGlobal('document', { cookie: '' })

    await expect(api('/api/projects')).resolves.toEqual([{ slug: 'acme' }])
    expect(fetchMock.mock.calls.map((c) => c[0])).toEqual([
      '/api/projects',
      '/api/auth/csrf',
      '/api/auth/refresh',
      '/api/projects',
    ])
  })

  it('throws the ProblemDetail message when refresh fails', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(response(401, { detail: 'Login required' }))
      .mockResolvedValueOnce(response(200))
      .mockResolvedValueOnce(response(401))
    vi.stubGlobal('fetch', fetchMock)
    vi.stubGlobal('document', { cookie: '' })

    const error = await api('/api/projects').catch((e) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(401)
    expect(error.message).toBe('Login required')
  })

  it('sends the CSRF header on POST', async () => {
    const fetchMock = vi.fn().mockResolvedValue(response(201, { ok: true }))
    vi.stubGlobal('fetch', fetchMock)
    vi.stubGlobal('document', { cookie: 'XSRF-TOKEN=tok' })

    await api('/api/projects', { method: 'POST', body: { name: 'x' } })
    expect(fetchMock.mock.calls[0][1].headers).toEqual({ 'X-XSRF-TOKEN': 'tok', 'Content-Type': 'application/json' })
  })
})
