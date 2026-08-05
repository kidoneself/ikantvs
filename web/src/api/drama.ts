/**
 * 短剧专区 API：列表 / 搜索 / 总数。链接为加密 token，前端走转存。
 */
import { http } from '@/api/http'

export interface DramaItem {
  id: number
  title: string
  episodeCount?: number
  /** 加密夸克链 token */
  quarkLink?: string
  /** 加密百度链 token */
  baiduLink?: string
  /** 如 /drama-covers/xxx.jpg */
  coverImage?: string
}

export interface DramaPage {
  records: DramaItem[]
  total: number
  page: number
  size: number
}

export function fetchDramaList(page = 1, size = 30): Promise<DramaPage> {
  return http.get<DramaPage>(`/drama/list?page=${page}&size=${size}`)
}

export function searchDrama(kw: string, page = 1, size = 30): Promise<DramaPage> {
  const q = encodeURIComponent(kw.trim())
  return http.get<DramaPage>(`/drama/search?kw=${q}&page=${page}&size=${size}`)
}

export function fetchDramaCount(): Promise<number> {
  return http.get<number>('/drama/count')
}
