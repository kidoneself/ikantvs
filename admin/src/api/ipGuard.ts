import { http } from './http'

/** 黑名单条目：IP + 原因 + 剩余封禁秒数(ttl，-1=永久) + 是否永久。 */
export interface BlacklistItem {
  ip: string
  reason?: string
  ttl: number
  permanent: boolean
}

/** 可疑 IP：窗口内被限流命中次数，达阈值会自动封禁。 */
export interface SuspiciousItem {
  ip: string
  rateLimitCount: number
  windowTtl?: number
}

export interface BanRequest {
  ip: string
  /** 封禁时长（秒）；permanent=true 时忽略。 */
  durationSeconds?: number
  permanent?: boolean
  reason?: string
}

export function fetchBlacklist() {
  return http.get<BlacklistItem[]>(`/admin/ip-guard/blacklist`)
}

export function fetchSuspicious() {
  return http.get<SuspiciousItem[]>(`/admin/ip-guard/suspicious`)
}

export function banIp(body: BanRequest) {
  return http.post<void>(`/admin/ip-guard/ban`, body)
}

export function unbanIp(ip: string) {
  return http.post<void>(`/admin/ip-guard/unban?ip=${encodeURIComponent(ip)}`)
}
