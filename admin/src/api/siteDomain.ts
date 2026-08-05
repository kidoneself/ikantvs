import { http } from './http'

export interface PanOption {
  slug: string
  label: string
}

export interface SiteDomainConfig {
  id: number
  host: string
  enabled: boolean
  pans: Record<string, boolean>
  remark?: string
}

export interface SiteDomainSaveBody {
  host: string
  enabled: boolean
  pans: Record<string, boolean>
  remark?: string
}

export function fetchPanOptions() {
  return http.get<PanOption[]>('/admin/site-domains/pan-options')
}

export function fetchSiteDomains() {
  return http.get<SiteDomainConfig[]>('/admin/site-domains')
}

export function createSiteDomain(body: SiteDomainSaveBody) {
  return http.post<SiteDomainConfig>('/admin/site-domains', body)
}

export function updateSiteDomain(id: number, body: SiteDomainSaveBody) {
  return http.put<SiteDomainConfig>(`/admin/site-domains/${id}`, body)
}

export function deleteSiteDomain(id: number) {
  return http.del<void>(`/admin/site-domains/${id}`)
}
