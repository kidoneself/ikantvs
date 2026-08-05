/**
 * 前台行为埋点：即发即忘，绝不抛错、不阻塞页面。
 * 收集搜索词/网盘点击等，供后台「数据洞察」与热度计算。
 */
import { http } from '@/api/http'

export type TrackType = 'search' | 'link_click' | 'card_click'

interface TrackPayload {
  mediaId?: number
  keyword?: string
  tag?: string
  num?: number
}

export function track(type: TrackType, payload: TrackPayload = {}): void {
  http.post('/events', { type, ...payload }).catch(() => {})
}
