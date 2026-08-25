import { http } from './http'

export interface PoolIngestRow {
  id?: number
  title: string
  panType: string
  panLabel: string
  url?: string
  shareUrl?: string
  status: string
  reason?: string
}

export interface PoolIngestResult {
  added: number
  updated: number
  skipped: number
  failed: number
  rows: PoolIngestRow[]
}

export function ingestPeer(text: string) {
  return http.post<PoolIngestResult>('/admin/pool/ingest', { text })
}

export function ingestSelf(text: string) {
  return http.post<PoolIngestResult>('/admin/pool/self', { text })
}

export function fetchSelfProgress(id: number) {
  return http.get<PoolIngestRow>(`/admin/pool/self?id=${id}`)
}

export const POOL_STATUS_LABEL: Record<string, string> = {
  added: '新增',
  updated: '已更新',
  skipped: '跳过',
  failed: '失败',
  transferring: '转存中',
  done: '完成',
}

export const POOL_STATUS_TYPE: Record<string, 'success' | 'warning' | 'info' | 'danger'> = {
  added: 'success',
  updated: 'success',
  skipped: 'info',
  failed: 'danger',
  transferring: 'warning',
  done: 'success',
}
