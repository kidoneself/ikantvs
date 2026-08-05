import { http } from './http'
import type { AdminMedia } from './media'

export interface RankingItemMedia {
  id: number
  title?: string
  poster?: string
  type?: string
  year?: number
  rating?: number
  pubStatus?: number
}

export interface Ranking {
  id: number
  name: string
  slug: string
  description?: string
  sort: number
  enabled: number
  itemCount?: number
  items?: RankingItemMedia[]
}

export interface RankingSaveBody {
  id?: number
  name: string
  slug: string
  description?: string
  sort?: number
  enabled?: number
}

export function fetchRankings() {
  return http.get<Ranking[]>('/admin/rankings')
}

export function fetchRanking(id: number) {
  return http.get<Ranking>(`/admin/rankings/${id}`)
}

export function saveRanking(body: RankingSaveBody) {
  return http.post<Ranking>('/admin/rankings', body)
}

export function deleteRanking(id: number) {
  return http.del<void>(`/admin/rankings/${id}`)
}

export function setRankingItems(id: number, mediaIds: number[]) {
  return http.put<Ranking>(`/admin/rankings/${id}/items`, { mediaIds })
}

export type { AdminMedia }
