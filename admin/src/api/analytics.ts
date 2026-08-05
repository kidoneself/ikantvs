import { http } from './http'

export interface KeywordStat {
  keyword: string
  cnt: number
}

export interface MediaStat {
  mediaId: number
  cnt: number
  title?: string
  poster?: string
  type?: string
}

/** 带环比：相对上一同长周期。changePct 为 null 表示上期为 0。 */
export interface MetricDelta {
  current: number
  previous: number
  changePct: number | null
}

export interface AnalyticsOverview {
  days: number
  visitors: MetricDelta
  searches: MetricDelta
  cardClicks: MetricDelta
  linkClicks: MetricDelta
  topSearches: KeywordStat[]
  demandGaps: KeywordStat[]
  topCardClicked: MediaStat[]
  topLinkClicked: MediaStat[]
}

export function fetchOverview(days = 7) {
  return http.get<AnalyticsOverview>(`/admin/analytics/overview?days=${days}`)
}

/** 环比文案：↑12.3% / ↓5% / 新增 / — */
export function formatChange(m: MetricDelta | null | undefined): string {
  if (!m) return '—'
  if (m.changePct == null) {
    return m.current > 0 ? '新增' : '—'
  }
  const sign = m.changePct > 0 ? '+' : ''
  return `${sign}${m.changePct}%`
}

export function changeTone(m: MetricDelta | null | undefined): 'up' | 'down' | 'flat' {
  if (!m || m.changePct == null) return m && m.current > 0 ? 'up' : 'flat'
  if (m.changePct > 0) return 'up'
  if (m.changePct < 0) return 'down'
  return 'flat'
}

export function formatCount(n: number | null | undefined): string {
  return (n ?? 0).toLocaleString()
}
