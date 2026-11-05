import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from './client'

/*
 * Shapes returned by the API (for reference):
 *   Role       'OWNER' | 'MEMBER' | 'VIEWER'
 *   TestState  'NEW' | 'STABLE' | 'SUSPECT' | 'FLAKY' | 'BROKEN'
 *   User       { id, email, displayName }
 *   Project    { slug, name, defaultBranch, role, createdAt }
 *   ApiKey     { id, name, prefix, createdAt, lastUsedAt, revokedAt }
 *   Member     { userId, email, displayName, role }
 *   RunSummary { id, commitSha, branch, ciProvider, buildUrl, triggeredAt, durationMs,
 *                total, passed, failed, skipped, flaky }
 *   ResultView { testCaseId, suite, name, file, outcome, attempts, failedAttempts, durationMs,
 *                failureType, message, stack, state, signatureId }
 *              outcome: 'PASSED' | 'FAILED' | 'SKIPPED' | 'PASSED_ON_RETRY'
 *   RunDetail  { run: RunSummary, failed: ResultView[], flaky: ResultView[], slowest: ResultView[] }
 *   TestCase   { id, suite, name, file, state, score, windowOutcomes, runsInWindow, flipCount,
 *                retryPassCount, failStreak, lastSeen }
 *   Signature  { id, exceptionType, normalizedMessage, sampleStack, occurrences, affectedTests,
 *                firstSeen, lastSeen }
 *   Upload     { jobId, status: 'QUEUED' | 'PROCESSING' | 'DONE' | 'FAILED', attempts, error, runId,
 *                fileCount, createdAt, finishedAt }
 *   Page<T>    { content: T[], page, size, totalElements }
 */

// --- session ---

/** The logged-in user, or null when logged out. */
export function useMe() {
  return useQuery({
    queryKey: ['me'],
    queryFn: () =>
      api('/api/auth/me').catch((e) => {
        if (e instanceof ApiError && e.status === 401) return null
        throw e
      }),
    staleTime: 60_000,
  })
}

export function useLogin() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body) => api('/api/auth/login', { method: 'POST', body }),
    onSuccess: (user) => {
      qc.clear()
      qc.setQueryData(['me'], user)
    },
  })
}

export function useSignup() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body) => api('/api/auth/signup', { method: 'POST', body }),
    onSuccess: (user) => {
      qc.clear()
      qc.setQueryData(['me'], user)
    },
  })
}

export function useLogout() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => api('/api/auth/logout', { method: 'POST' }),
    onSettled: () => {
      qc.clear()
      qc.setQueryData(['me'], null)
    },
  })
}

// --- projects ---

export function useProjects() {
  return useQuery({ queryKey: ['projects'], queryFn: () => api('/api/projects') })
}

export function useProject(slug) {
  return useQuery({ queryKey: ['projects', slug], queryFn: () => api(`/api/projects/${slug}`) })
}

export function useCreateProject() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body) => api('/api/projects', { method: 'POST', body }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['projects'] }),
  })
}

export function useDeleteProject(slug) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => api(`/api/projects/${slug}`, { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['projects'] }),
  })
}

// --- runs & uploads ---

export function useRuns(slug, page, branch, size = 20) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (branch) params.set('branch', branch)
  return useQuery({
    queryKey: ['runs', slug, page, branch, size],
    queryFn: () => api(`/api/projects/${slug}/runs?${params}`),
  })
}

export function useRun(slug, id) {
  return useQuery({ queryKey: ['run', slug, id], queryFn: () => api(`/api/projects/${slug}/runs/${id}`) })
}

export function useBranches(slug) {
  return useQuery({ queryKey: ['branches', slug], queryFn: () => api(`/api/projects/${slug}/branches`) })
}

/** Recent uploads; polls while any upload is still waiting or being processed. */
export function useUploads(slug) {
  return useQuery({
    queryKey: ['uploads', slug],
    queryFn: () => api(`/api/projects/${slug}/uploads`),
    refetchInterval: (query) =>
      query.state.data?.some((u) => u.status === 'QUEUED' || u.status === 'PROCESSING') ? 3000 : false,
  })
}

// --- tests & signatures ---

/** @param {{ state?: string, q?: string, sort?: string, page?: number, size?: number }} opts */
export function useTests(slug, opts) {
  const params = new URLSearchParams({
    page: String(opts.page ?? 0),
    size: String(opts.size ?? 25),
    sort: opts.sort ?? 'score',
  })
  if (opts.state) params.set('state', opts.state)
  if (opts.q) params.set('q', opts.q)
  return useQuery({
    queryKey: ['tests', slug, params.toString()],
    queryFn: () => api(`/api/projects/${slug}/tests?${params}`),
  })
}

export function useTestSummary(slug) {
  return useQuery({
    queryKey: ['tests-summary', slug],
    queryFn: () => api(`/api/projects/${slug}/tests/summary`),
  })
}

export function useSignatures(slug, page) {
  return useQuery({
    queryKey: ['signatures', slug, page],
    queryFn: () => api(`/api/projects/${slug}/signatures?page=${page}`),
  })
}

// --- dashboard & test detail ---

/** KPIs for the last `days` days (7, 30 or 90), with the previous period for comparison. */
export function useSummary(slug, days) {
  return useQuery({ queryKey: ['summary', slug, days], queryFn: () => api(`/api/projects/${slug}/summary?days=${days}`) })
}

/** One point per day: { day, runs, executed, passed, failed, flaky, passRate, avgRunDurationMs }. */
export function useTrends(slug, days) {
  return useQuery({ queryKey: ['trends', slug, days], queryFn: () => api(`/api/projects/${slug}/trends?days=${days}`) })
}

export function useNewlyFlaky(slug) {
  return useQuery({ queryKey: ['newly-flaky', slug], queryFn: () => api(`/api/projects/${slug}/tests/newly-flaky`) })
}

/** { test, firstSeen, breakdown, durations, history, signatures } */
export function useTestDetail(slug, id) {
  return useQuery({ queryKey: ['test', slug, id], queryFn: () => api(`/api/projects/${slug}/tests/${id}`) })
}

/** The test's last results, newest first. */
export function useTestResults(slug, id, limit = 50) {
  return useQuery({
    queryKey: ['test-results', slug, id, limit],
    queryFn: () => api(`/api/projects/${slug}/tests/${id}/results?limit=${limit}`),
  })
}

// --- API keys ---

export function useApiKeys(slug) {
  return useQuery({ queryKey: ['api-keys', slug], queryFn: () => api(`/api/projects/${slug}/api-keys`) })
}

export function useCreateApiKey(slug) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (name) => api(`/api/projects/${slug}/api-keys`, { method: 'POST', body: { name } }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['api-keys', slug] }),
  })
}

export function useRevokeApiKey(slug) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id) => api(`/api/projects/${slug}/api-keys/${id}`, { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['api-keys', slug] }),
  })
}

// --- members ---

export function useMembers(slug) {
  return useQuery({ queryKey: ['members', slug], queryFn: () => api(`/api/projects/${slug}/members`) })
}

export function useAddMember(slug) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body) => api(`/api/projects/${slug}/members`, { method: 'POST', body }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['members', slug] }),
  })
}

export function useChangeRole(slug) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ userId, role }) =>
      api(`/api/projects/${slug}/members/${userId}`, { method: 'PATCH', body: { role } }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['members', slug] }),
  })
}

export function useRemoveMember(slug) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (userId) => api(`/api/projects/${slug}/members/${userId}`, { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['members', slug] }),
  })
}
