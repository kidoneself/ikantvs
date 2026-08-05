import { http } from './http'

export interface LiveQrcodeConfig {
  qrcodeImage: string
  mpQrcodeImage: string
  title: string
  tipText: string
  scanCount: number
  status: number
}

export interface LiveQrcodeStats {
  totalCount: number
  todayCount: number
  sourceStats: { source: string; count: number }[]
  trendStats: { date: string; count: number }[]
}

export function fetchLiveQrcodeConfig() {
  return http.get<LiveQrcodeConfig>('/admin/live-qrcode/config')
}

export function updateLiveQrcodeConfig(body: Partial<LiveQrcodeConfig>) {
  return http.put<LiveQrcodeConfig>('/admin/live-qrcode/config', body)
}

export function fetchLiveQrcodeStats() {
  return http.get<LiveQrcodeStats>('/admin/live-qrcode/stats')
}
