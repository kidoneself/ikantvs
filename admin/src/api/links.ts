import { http } from './http'

export interface AdminMediaLink {
  id: number
  mediaId: number
  mediaTitle?: string
  panType?: string
  panLabel?: string
  url?: string
  note?: string
  source?: string
  status?: string
  invalid?: number
  checkState?: string | null
  updatedAt?: string
}

interface ApiPage<T> {
  total: number
  page: number
  size: number
  records: T[]
}

export interface LinkSearchParams {
  page?: number
  size?: number
  q?: string
  panType?: string
  source?: string
  invalid?: number
  mediaId?: number
}

export function fetchLinkList(params: LinkSearchParams) {
  const qs = new URLSearchParams({
    page: String(params.page ?? 1),
    size: String(params.size ?? 20),
  })
  if (params.q?.trim()) qs.set('q', params.q.trim())
  if (params.panType) qs.set('panType', params.panType)
  if (params.source) qs.set('source', params.source)
  if (params.invalid != null) qs.set('invalid', String(params.invalid))
  if (params.mediaId != null) qs.set('mediaId', String(params.mediaId))
  return http.get<ApiPage<AdminMediaLink>>(`/admin/media-links?${qs}`)
}
