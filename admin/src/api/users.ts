import { http } from './http'

export interface AdminUser {
  id: number
  username: string
  nickname?: string
  avatar?: string
  role: string
  status: number
  lastLoginAt?: string
  createdAt?: string
}

interface ApiPage<T> {
  total: number
  page: number
  size: number
  records: T[]
}

export function fetchUsers(params: {
  page?: number
  size?: number
  q?: string
  role?: string
  status?: number
}) {
  const { page = 1, size = 20, q, role, status } = params
  const qs = new URLSearchParams({ page: String(page), size: String(size) })
  if (q?.trim()) qs.set('q', q.trim())
  if (role) qs.set('role', role)
  if (status != null) qs.set('status', String(status))
  return http.get<ApiPage<AdminUser>>(`/admin/users?${qs}`)
}

export function updateUserRole(id: number, role: string) {
  return http.put<AdminUser>(`/admin/users/${id}/role`, { role })
}

export function updateUserStatus(id: number, status: number) {
  return http.put<AdminUser>(`/admin/users/${id}/status`, { status })
}
