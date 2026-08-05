import { http } from './http'

export interface ParseRules {
  template?: string
  quarkPrefixes?: string[]
  baiduPrefixes?: string[]
  xunleiPrefixes?: string[]
  noisePrefixes?: string[]
  noiseContains?: string[]
  nameExtractRegex?: string
  pwdRegex?: string
  matchMode?: 'startsWith' | 'labeled' | string
}

export interface DocMonitorTask {
  id: number
  source: string
  taskName: string
  shareUrl: string
  accessCode?: string
  category?: string
  status: number
  parseRules?: ParseRules
  contentHash?: string
  linksCount?: number
  textLength?: number
  dramaCount?: number
  lastCheckTime?: string
  lastUpdateTime?: string
  remark?: string
  createdAt?: string
  updatedAt?: string
}

export interface DramaEntry {
  name?: string
  fullTitle?: string
  quarkUrl?: string
  baiduUrl?: string
  xunleiUrl?: string
  source?: string
}

export interface PreviewResult {
  source: string
  title?: string
  linksCount: number
  textLength: number
  dramaCount: number
  fingerprint?: string
  appliedRules?: ParseRules
  dramas?: DramaEntry[]
  sampleLinks?: { url: string; type: string; text?: string }[]
}

export interface CheckResult {
  taskId: number
  taskName?: string
  source?: string
  success: boolean
  unchanged?: boolean
  updated?: boolean
  message?: string
  linksCount?: number
  dramaCount?: number
}

export interface HistoryItem {
  id: number
  taskId: number
  source?: string
  taskName?: string
  oldLinksCount?: number
  newLinksCount?: number
  linksCountDiff?: number
  hasUpdate?: number
  changeDescription?: string
  checkType?: string
  createdAt?: string
}

interface ApiPage<T> {
  total: number
  page: number
  size: number
  records: T[]
}

export function fetchMeta() {
  return http.get<{ sources: string[]; templates: Record<string, ParseRules> }>('/admin/doc-monitor/meta')
}

export function fetchTasks(params: { page?: number; size?: number; keyword?: string; source?: string }) {
  const q = new URLSearchParams()
  if (params.page) q.set('page', String(params.page))
  if (params.size) q.set('size', String(params.size))
  if (params.keyword) q.set('keyword', params.keyword)
  if (params.source) q.set('source', params.source)
  const qs = q.toString()
  return http.get<ApiPage<DocMonitorTask>>(`/admin/doc-monitor/tasks${qs ? `?${qs}` : ''}`)
}

export function createTask(body: {
  source?: string
  taskName?: string
  shareUrl: string
  accessCode?: string
  category?: string
  remark?: string
  parseRules?: ParseRules
  template?: string
}) {
  return http.post<DocMonitorTask>('/admin/doc-monitor/tasks', body)
}

export function updateTask(
  id: number,
  body: {
    source?: string
    taskName?: string
    shareUrl: string
    accessCode?: string
    category?: string
    status?: number
    remark?: string
    parseRules?: ParseRules
    template?: string
  },
) {
  return http.put<DocMonitorTask>(`/admin/doc-monitor/tasks/${id}`, body)
}

export function deleteTask(id: number) {
  return http.del<void>(`/admin/doc-monitor/tasks/${id}`)
}

export function setTaskStatus(id: number, enabled: boolean) {
  return http.put<void>(`/admin/doc-monitor/tasks/${id}/status`, { enabled })
}

export function previewParse(body: {
  source?: string
  shareUrl: string
  accessCode?: string
  parseRules?: ParseRules
  template?: string
}) {
  return http.post<PreviewResult>('/admin/doc-monitor/preview', body)
}

export function checkTask(id: number) {
  return http.post<CheckResult>(`/admin/doc-monitor/tasks/${id}/check`)
}

export function checkAll() {
  return http.post<CheckResult[]>('/admin/doc-monitor/check-all')
}

export function fetchHistory(id: number, limit = 30) {
  return http.get<HistoryItem[]>(`/admin/doc-monitor/tasks/${id}/history?limit=${limit}`)
}
