import { http } from './http'

export interface ContentSyncStatus {
  task: string
  taskLabel: string
  running: boolean
  phase: string
  total: number
  processed: number
  affected: number
  result?: string
  error?: string
  startedAt?: number | null
  finishedAt?: number | null
}

export type SyncTask = 'rankings'

/** 触发重建榜单（异步，立即返回进度）。 */
export function triggerSync(task: SyncTask) {
  return http.post<ContentSyncStatus>('/admin/content-sync/rebuild-rankings', {})
}

/** 查询当前同步进度（轮询用）。 */
export function fetchSyncStatus() {
  return http.get<ContentSyncStatus>('/admin/content-sync/status')
}
