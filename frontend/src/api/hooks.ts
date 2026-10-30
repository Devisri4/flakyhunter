import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from './client'

export type Role = 'OWNER' | 'MEMBER' | 'VIEWER'

export type User = { id: string; email: string; displayName: string }

export type Project = { slug: string; name: string; defaultBranch: string; role: Role; createdAt: string }

export type ApiKey = {
  id: string
  name: string
  prefix: string
  createdAt: string
  lastUsedAt: string | null
  revokedAt: string | null
}

export type Member = { userId: string; email: string; displayName: string; role: Role }

// --- session ---

/** The logged-in user, or null when logged out. */
export function useMe() {
  return useQuery({
    queryKey: ['me'],
    queryFn: () =>
      api<User>('/api/auth/me').catch((e: unknown) => {
        if (e instanceof ApiError && e.status === 401) return null
        throw e
      }),
    staleTime: 60_000,
  })
}

export function useLogin() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: { email: string; password: string }) =>
      api<User>('/api/auth/login', { method: 'POST', body }),
    onSuccess: (user) => {
      qc.clear()
      qc.setQueryData(['me'], user)
    },
  })
}

export function useSignup() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: { email: string; password: string; displayName: string }) =>
      api<User>('/api/auth/signup', { method: 'POST', body }),
    onSuccess: (user) => {
      qc.clear()
      qc.setQueryData(['me'], user)
    },
  })
}

export function useLogout() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => api<void>('/api/auth/logout', { method: 'POST' }),
    onSettled: () => {
      qc.clear()
      qc.setQueryData(['me'], null)
    },
  })
}

// --- projects ---

export function useProjects() {
  return useQuery({ queryKey: ['projects'], queryFn: () => api<Project[]>('/api/projects') })
}

export function useProject(slug: string) {
  return useQuery({ queryKey: ['projects', slug], queryFn: () => api<Project>(`/api/projects/${slug}`) })
}

export function useCreateProject() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: { name: string; slug?: string }) => api<Project>('/api/projects', { method: 'POST', body }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['projects'] }),
  })
}

export function useDeleteProject(slug: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => api<void>(`/api/projects/${slug}`, { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['projects'] }),
  })
}

// --- runs & uploads ---

export type RunSummary = {
  id: number
  commitSha: string | null
  branch: string | null
  ciProvider: string | null
  buildUrl: string | null
  triggeredAt: string
  durationMs: number
  total: number
  passed: number
  failed: number
  skipped: number
  flaky: number
}

export type ResultView = {
  testCaseId: number
  suite: string
  name: string
  file: string | null
  outcome: 'PASSED' | 'FAILED' | 'SKIPPED' | 'PASSED_ON_RETRY'
  attempts: number
  failedAttempts: number
  durationMs: number
  failureType: string | null
  message: string | null
  stack: string | null
}

export type RunDetail = { run: RunSummary; failed: ResultView[]; flaky: ResultView[]; slowest: ResultView[] }

export type Page<T> = { content: T[]; page: number; size: number; totalElements: number }

export type Upload = {
  jobId: string
  status: 'QUEUED' | 'PROCESSING' | 'DONE' | 'FAILED'
  attempts: number
  error: string | null
  runId: number | null
  fileCount: number
  createdAt: string
  finishedAt: string | null
}

export function useRuns(slug: string, page: number, branch: string, size = 20) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (branch) params.set('branch', branch)
  return useQuery({
    queryKey: ['runs', slug, page, branch, size],
    queryFn: () => api<Page<RunSummary>>(`/api/projects/${slug}/runs?${params}`),
  })
}

export function useRun(slug: string, id: string) {
  return useQuery({ queryKey: ['run', slug, id], queryFn: () => api<RunDetail>(`/api/projects/${slug}/runs/${id}`) })
}

export function useBranches(slug: string) {
  return useQuery({ queryKey: ['branches', slug], queryFn: () => api<string[]>(`/api/projects/${slug}/branches`) })
}

/** Recent uploads; polls while any upload is still waiting or being processed. */
export function useUploads(slug: string) {
  return useQuery({
    queryKey: ['uploads', slug],
    queryFn: () => api<Upload[]>(`/api/projects/${slug}/uploads`),
    refetchInterval: (query) =>
      query.state.data?.some((u) => u.status === 'QUEUED' || u.status === 'PROCESSING') ? 3000 : false,
  })
}

// --- API keys ---

export function useApiKeys(slug: string) {
  return useQuery({ queryKey: ['api-keys', slug], queryFn: () => api<ApiKey[]>(`/api/projects/${slug}/api-keys`) })
}

export function useCreateApiKey(slug: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (name: string) =>
      api<{ apiKey: ApiKey; key: string }>(`/api/projects/${slug}/api-keys`, { method: 'POST', body: { name } }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['api-keys', slug] }),
  })
}

export function useRevokeApiKey(slug: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api<void>(`/api/projects/${slug}/api-keys/${id}`, { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['api-keys', slug] }),
  })
}

// --- members ---

export function useMembers(slug: string) {
  return useQuery({ queryKey: ['members', slug], queryFn: () => api<Member[]>(`/api/projects/${slug}/members`) })
}

export function useAddMember(slug: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: { email: string; role: Role }) =>
      api<Member>(`/api/projects/${slug}/members`, { method: 'POST', body }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['members', slug] }),
  })
}

export function useChangeRole(slug: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: Role }) =>
      api<void>(`/api/projects/${slug}/members/${userId}`, { method: 'PATCH', body: { role } }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['members', slug] }),
  })
}

export function useRemoveMember(slug: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (userId: string) => api<void>(`/api/projects/${slug}/members/${userId}`, { method: 'DELETE' }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['members', slug] }),
  })
}
