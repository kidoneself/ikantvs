import { http } from './http'

export interface AdminMedia {
  id: number
  tmdbId?: number
  doubanId?: string
  type?: string
  title?: string
  originalTitle?: string
  year?: number
  poster?: string
  posterThumb?: string
  backdrop?: string
  rating?: number
  overview?: string
  episodeCount?: number
  seasonCount?: number
  seriesStatus?: string
  inProduction?: boolean
  lastAirDate?: string
  lastSeasonNumber?: number
  lastEpisodeNumber?: number
  metaSource?: string
  pubStatus?: number
  hot?: number
  tier?: number
  searchHidden?: number
  updatedAt?: string
}

export interface SeasonVO {
  seasonNumber: number
  name?: string
  episodeCount?: number
  airDate?: string
  poster?: string
  overview?: string
}

export interface MediaDetail {
  media: AdminMedia
  seasons: SeasonVO[]
}

/** 兼容旧版扁平 MediaVO 与新版 MediaDetailVO。 */
export function normalizeMediaDetail(data: unknown): MediaDetail {
  if (data && typeof data === 'object' && 'media' in data) {
    const d = data as MediaDetail
    if (d.media) {
      return { media: d.media, seasons: d.seasons ?? [] }
    }
  }
  return { media: data as AdminMedia, seasons: [] }
}

export interface MediaUpdateBody {
  title?: string
  overview?: string
  poster?: string
  year?: number
  pubStatus?: number
  hot?: number
  tier?: number
  /** 1=关键词搜索不出现 */
  searchHidden?: number
  tmdbId?: number | null
}

export interface ManualMediaBody {
  title: string
  type?: string
  year?: number
  poster?: string
  overview?: string
  publish?: boolean
}

interface ApiPage<T> {
  total: number
  page: number
  size: number
  records: T[]
}

const TYPE_LABEL: Record<string, string> = {
  movie: '电影',
  tv: '剧集',
  anime: '动漫',
  variety: '综艺',
}

const SERIES_TYPES = new Set(['tv', 'anime', 'variety'])

export function typeLabel(type?: string) {
  return TYPE_LABEL[type || ''] || type || '-'
}

export function isSeriesType(type?: string) {
  return SERIES_TYPES.has(type || '')
}

/** 列表/详情缩略图：优先 posterThumb。 */
export function listPosterUrl(m: Pick<AdminMedia, 'poster' | 'posterThumb'>) {
  return m.posterThumb || m.poster || ''
}

/** 剧集季数/连载摘要。 */
export function seriesSummary(m: AdminMedia): string {
  if (!isSeriesType(m.type)) return ''
  if (m.seasonCount == null) return ''
  if (m.seasonCount === 0) return '无季拆分'
  const parts: string[] = [`${m.seasonCount} 季`]
  if (m.episodeCount) parts.push(`全 ${m.episodeCount} 集`)
  if (m.inProduction && m.lastSeasonNumber && m.lastEpisodeNumber) {
    parts.push(`更新至 S${m.lastSeasonNumber}E${m.lastEpisodeNumber}`)
  } else if (m.seriesStatus) {
    parts.push(m.seriesStatus)
  }
  return parts.join(' · ')
}

export function fetchList(page = 1, size = 20, type?: string, q?: string, hidden?: boolean) {
  const qs = new URLSearchParams({ page: String(page), size: String(size) })
  if (type) qs.set('type', type)
  if (q?.trim()) qs.set('q', q.trim())
  if (hidden) qs.set('hidden', 'true')
  return http.get<ApiPage<AdminMedia>>(`/admin/media?${qs}`)
}

export function fetchDetail(id: number) {
  return http.get<MediaDetail>(`/admin/media/${id}`).then(normalizeMediaDetail)
}

export function updateMedia(id: number, body: MediaUpdateBody) {
  return http.put<AdminMedia>(`/admin/media/${id}`, body)
}

export function createManualMedia(body: ManualMediaBody) {
  return http.post<AdminMedia>('/admin/media/manual', body)
}

export function storageStatus() {
  return http.get<{ ready: boolean }>('/admin/media/storage-status')
}

export function publicSiteBase() {
  return (import.meta.env.VITE_PUBLIC_SITE as string) || 'http://localhost:5173'
}
