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

export type SyncTask = 'discover' | 'refresh' | 'rankings'

/** 触发拉新 / 刷新连载 / 重建榜单（异步，立即返回进度）。 */
export function triggerSync(task: SyncTask) {
  const path =
    task === 'discover'
      ? '/admin/content-sync/discover'
      : task === 'refresh'
        ? '/admin/content-sync/refresh-airing'
        : '/admin/content-sync/rebuild-rankings'
  return http.post<ContentSyncStatus>(path, {})
}

/** 查询当前同步进度（轮询用）。 */
export function fetchSyncStatus() {
  return http.get<ContentSyncStatus>('/admin/content-sync/status')
}
