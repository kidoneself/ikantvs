/**
 * 前台共用类型与导航常量（历史文件名 mock；已无假数据）。
 */

export type Category = '电影' | '剧集' | '动漫' | '综艺'

export const CATEGORIES: Array<'全部' | Category> = ['全部', '电影', '剧集', '动漫', '综艺']

/** 网盘展示标签（转存仅夸克/百度/迅雷） */
export type PanType =
  | '磁力'
  | '百度'
  | '夸克'
  | '迅雷'
  | 'UC'
  | '阿里'
  | '天翼'
  | '移动'
  | '115'
  | '123'
  | '其他'

export interface Download {
  rawTitle: string
  pan: PanType
  size: string
  updatedAt: string
  hasCode: boolean
}

export interface ResourceItem {
  id: number
  title: string
  aka?: string
  poster?: string
  posterThumb?: string
  backdrop?: string
  category: Category
  genres: string[]
  rating?: number
  year: number
  region: string
  languages: string[]
  director: string[]
  cast: string[]
  episodes?: number
  seasonCount?: number
  inProduction?: boolean
  lastSeasonNumber?: number
  lastEpisodeNumber?: number
  seriesStatus?: string
  airProgress?: string
  runtime?: string
  premiere?: string
  overview: string
  tags: string[]
  updatedAt: string
  downloads: Download[]
  hot?: boolean
  tmdbId?: number
  doubanId?: string
  mediaType?: string
}

/** 热搜占位；有接口后会被 MainLayout 覆盖 */
export const HOT_KEYWORDS = ['热门电影', '热门剧集', '最新上映', '高分佳片']
