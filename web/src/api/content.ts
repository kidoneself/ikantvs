/**
 * 内容域 API：对接后端 /api/media，映射为页面用的 ResourceItem。
 * 网盘链走 SSE 搜索（/api/search/stream），不按片拉详情链接。
 */
import { http } from '@/api/http'
import type { Category, PanType, ResourceItem } from '@/data/mock'

export type { ResourceItem, PanType, Category } from '@/data/mock'

export type SortKey = 'hot' | 'hot_asc' | 'new' | 'release_asc' | 'rating' | 'rating_asc'
/** 自动榜：最热 / 最新（最新=按上映时间，不是入库时间） */
export type RankKey = 'hot' | 'new'

export interface PageResult<T> {
  items: T[]
  total: number
  page: number
  size: number
  hasMore: boolean
}

export interface MediaVO {
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
  genres?: string[]
  country?: string[]
  actors?: string[]
  directors?: string[]
  releaseDate?: string
  episodeCount?: number
  seasonCount?: number
  seriesStatus?: string
  inProduction?: boolean
  lastAirDate?: string
  lastSeasonNumber?: number
  lastEpisodeNumber?: number
  hot?: number
  updatedAt?: string
  createdAt?: string
}

interface ApiPage<T> {
  total: number
  page: number
  size: number
  records: T[]
}

const TYPE_TO_CAT: Record<string, Category> = {
  movie: '电影',
  tv: '剧集',
  anime: '动漫',
  variety: '综艺',
}

const CAT_TO_TYPE: Record<string, string> = {
  电影: 'movie',
  剧集: 'tv',
  动漫: 'anime',
  综艺: 'variety',
}

export function mediaToResource(m: MediaVO): ResourceItem {
  const type = m.type || 'movie'
  return {
    id: m.id,
    title: m.title || '未命名',
    aka: m.originalTitle || undefined,
    poster: m.poster || undefined,
    posterThumb: m.posterThumb || undefined,
    backdrop: m.backdrop || undefined,
    category: TYPE_TO_CAT[type] || '电影',
    genres: m.genres?.length ? m.genres : [],
    rating: m.rating != null ? Number(m.rating) : undefined,
    year: m.year ?? 0,
    region: m.country?.[0] || '',
    languages: [],
    director: m.directors?.length ? m.directors : [],
    cast: m.actors?.length ? m.actors : [],
    episodes: m.episodeCount ?? undefined,
    seasonCount: m.seasonCount ?? undefined,
    inProduction: m.inProduction ?? undefined,
    lastSeasonNumber: m.lastSeasonNumber ?? undefined,
    lastEpisodeNumber: m.lastEpisodeNumber ?? undefined,
    seriesStatus: m.seriesStatus ?? undefined,
    airProgress: buildAirProgress(m),
    premiere: m.releaseDate || undefined,
    overview: m.overview || '',
    tags: m.genres?.length ? m.genres.slice(0, 5) : [],
    updatedAt: (m.updatedAt || m.createdAt || '').slice(0, 10),
    downloads: [],
    hot: (m.hot ?? 0) > 0,
    tmdbId: m.tmdbId ?? undefined,
    doubanId: m.doubanId?.trim() || undefined,
    mediaType: type,
  }
}

function buildAirProgress(m: MediaVO): string | undefined {
  const isSeries = m.type === 'tv' || m.type === 'anime' || m.type === 'variety'
  if (!isSeries) return undefined
  if (m.inProduction && m.lastSeasonNumber && m.lastEpisodeNumber) {
    return `更新至 S${m.lastSeasonNumber}E${m.lastEpisodeNumber}`
  }
  if (m.seasonCount && m.episodeCount) {
    return `${m.seasonCount} 季 · 全 ${m.episodeCount} 集`
  }
  if (m.episodeCount) {
    return `全 ${m.episodeCount} 集`
  }
  if (m.seasonCount) {
    return `${m.seasonCount} 季`
  }
  return undefined
}

function toPageResult<T>(p: ApiPage<T>, map: (x: T) => ResourceItem): PageResult<ResourceItem> {
  const items = p.records.map(map)
  return {
    items,
    total: p.total,
    page: p.page,
    size: p.size,
    hasMore: p.page * p.size < p.total,
  }
}

/** 「大家在搜」：近 N 天真实搜索热词（后端已过滤敏感/过短并缓存）。失败返回空数组，不阻塞首页。 */
export async function fetchHotSearches(limit = 10): Promise<string[]> {
  try {
    const data = await http.get<string[]>(`/events/hot-searches?limit=${limit}`)
    return Array.isArray(data) ? data : []
  } catch {
    return []
  }
}

/** 后端「已更新」一条：策展看板（带真实追更集数）或近期新增资源兜底。 */
interface DailyFeedItem {
  media: MediaVO
  /** 「更新至第 X 集」等真实追更集数；兜底项为空。 */
  updateNote?: string
  /** 真实更新时间（策展=补到新集数时间，兜底=资源最近出现时间）。 */
  updatedAt?: string
  /** 后台每日更新条目（固定 true）。 */
  curated?: boolean
}

function dailyToResource(it: DailyFeedItem): ResourceItem {
  const r = mediaToResource(it.media)
  // 用真实追更集数覆盖「更新至 SxEx」推断值
  if (it.updateNote) r.airProgress = it.updateNote
  if (it.updatedAt) r.updatedAt = it.updatedAt.slice(0, 10)
  return r
}

/** 首页「已更新」：后台录入的每日更新看板（不含新链自动补位）。 */
export async function fetchUpdatedFeed(params: {
  page?: number
  size?: number
}): Promise<PageResult<ResourceItem>> {
  const { page = 1, size = 12 } = params
  const qs = new URLSearchParams({ page: String(page), size: String(size) })
  const data = await http.get<ApiPage<DailyFeedItem>>(`/daily?${qs}`)
  return toPageResult(data, dailyToResource)
}

/** 首页信息流（分页 + 排序 + 可选分类） */
export async function fetchFeed(params: {
  sort?: SortKey
  cat?: string
  page?: number
  size?: number
}): Promise<PageResult<ResourceItem>> {
  const { sort = 'new', cat = '全部', page = 1, size = 12 } = params
  const qs = new URLSearchParams({ page: String(page), size: String(size), sort })
  const type = CAT_TO_TYPE[cat]
  if (type) qs.set('type', type)

  const data = await http.get<ApiPage<MediaVO>>(`/media?${qs}`)
  return toPageResult(data, mediaToResource)
}

/** 搜索 / 分类浏览：服务端按 关键词 + 类型 + 年份 + 题材 + 地区 + 评分 分页，返回真实总数。 */
export async function searchFeed(params: {
  q?: string
  cat?: string
  page?: number
  size?: number
  sort?: SortKey
  yearFrom?: number
  yearTo?: number
  genre?: string
  country?: string
  minRating?: number
}): Promise<PageResult<ResourceItem>> {
  const {
    q = '', cat = '全部', page = 1, size = 24, sort = 'hot',
    yearFrom, yearTo, genre, country, minRating,
  } = params
  const qs = new URLSearchParams({ page: String(page), size: String(size), sort })
  const type = CAT_TO_TYPE[cat]
  if (type) qs.set('type', type)
  if (q.trim()) qs.set('q', q.trim())
  if (yearFrom != null) qs.set('yearFrom', String(yearFrom))
  if (yearTo != null) qs.set('yearTo', String(yearTo))
  if (genre) qs.set('genre', genre)
  if (country) qs.set('country', country)
  if (minRating != null && minRating > 0) qs.set('minRating', String(minRating))
  const data = await http.get<ApiPage<MediaVO>>(`/media?${qs}`)
  return toPageResult(data, mediaToResource)
}

export async function fetchRanking(type: RankKey): Promise<ResourceItem[]> {
  const res = await fetchFeed({ sort: type, page: 1, size: 50 })
  return res.items
}

export interface RankingBoard {
  id: number
  name: string
  slug: string
  description?: string
  items: ResourceItem[]
}

interface RankingVO {
  id: number
  name: string
  slug: string
  description?: string
  items?: MediaVO[]
}

/** 后台策划榜单（已上架）。无数据时返回空数组，前台回退到自动榜。 */
export async function fetchRankingBoards(): Promise<RankingBoard[]> {
  try {
    const data = await http.get<RankingVO[]>('/rankings')
    return data
      .map((r) => ({
        id: r.id,
        name: r.name,
        slug: r.slug,
        description: r.description,
        items: (r.items || []).map(mediaToResource),
      }))
      .filter((b) => b.items.length > 0)
  } catch {
    return []
  }
}
