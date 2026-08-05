import { http } from './http'

export interface DashboardStats {
  total: number
  published: number
  draft: number
  offline: number
  byType: Record<string, number>
  r2Ready: boolean
}

export function fetchDashboardStats() {
  return http.get<DashboardStats>('/admin/dashboard/stats')
}
