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
