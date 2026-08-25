import { http } from './http'

export interface TransferJob {
  id: number
  jobType: string
  panType: string
  shareUrl: string
  sharePwd?: string
  mediaLinkId?: number
  targetFolderId?: string
  status: string
  priority: number
  attempts: number
  maxAttempts: number
  availableAt?: string
  workerId?: string
  leaseUntil?: string
  resultJson?: string
  resultShareUrl?: string
  resultFolderId?: string
  errorMsg?: string
  createdAt?: string
  updatedAt?: string
}

export interface TransferMonitor {
  id: number
  mediaLinkId: number
  panType: string
  shareUrl: string
  sharePwd?: string
  enabled: boolean
  status: string
  targetFolderId?: string
  myShareUrl?: string
  lastUpdatedAt?: number
  lastFileCount?: number
  lastTitle?: string
  latestEpisode?: string
  lastProbeAt?: string
  failCount?: number
  createdAt?: string
  updatedAt?: string
}

export interface TransferAccount {
  id: number
  workerId: string
  panType: string
  accountName: string
  /** transfer=用户转存号 / monitor=每日更新监控号 */
  role?: string
  nickname?: string
  uid?: string
  totalSpace?: number
  usedSpace?: number
  enabled: boolean
  healthy: boolean
  /** 是否已有凭据；false=从未登录（空壳号），此时 healthy 无意义 */
  hasCredential?: boolean
  /** 百度删除令牌：是否已授权（仅百度号有意义） */
  hasBaiduToken?: boolean
  /** 百度删除令牌是否已过期 */
  baiduTokenExpired?: boolean
  /** 百度删除令牌剩余天数（负=已过期） */
  baiduTokenDaysLeft?: number
  removing?: boolean
  note?: string
  lastSeenAt?: string
  createdAt?: string
  updatedAt?: string
}

export interface LoginSession {
  sessionId: string
  panType: string
  /** cookie / oauth */
  mode?: string
  /** pending / pending_auth / claimed / success / failed / expired */
  status: string
  accountName?: string
  message?: string
}

interface ApiPage<T> {
  total: number
  page: number
  size: number
  records: T[]
}

/** 粘贴 cookie 加号的网盘（迅雷走 OAuth，不在此列）。 */
export const COOKIE_PAN_TYPES = ['quark', 'baidu']

export const LOGIN_STATUS_LABEL: Record<string, string> = {
  pending: '落号中',
  pending_auth: '等待浏览器授权',
  claimed: '正在落号',
  success: '成功',
  failed: '失败',
  expired: '已过期',
}

export const PAN_LABEL: Record<string, string> = {
  quark: '夸克',
  baidu: '百度',
  xunlei: '迅雷',
}

export const JOB_TYPE_LABEL: Record<string, string> = {
  check: '检查',
  create: '创建',
  update: '更新',
  transfer: '转存',
  delete: '清理',
  // 旧值兜底（迁移前残留）
  probe: '检查',
  first_save: '创建/转存',
  sync: '更新',
}

export const JOB_STATUS_LABEL: Record<string, string> = {
  pending: '排队中',
  running: '执行中',
  done: '完成',
  failed: '失败',
  canceled: '已取消',
}

export const JOB_STATUS_TYPE: Record<string, '' | 'success' | 'warning' | 'info' | 'danger'> = {
  pending: 'info',
  running: 'warning',
  done: 'success',
  failed: 'danger',
  canceled: '',
}

export const MONITOR_STATUS_LABEL: Record<string, string> = {
  active: '监控中',
  invalid: '死链',
  paused: '暂停',
}

export const MONITOR_STATUS_TYPE: Record<string, '' | 'success' | 'warning' | 'info' | 'danger'> = {
  active: 'success',
  invalid: 'danger',
  paused: 'info',
}

export function listJobs(params: { page?: number; size?: number; status?: string; panType?: string }) {
  const { page = 1, size = 20, status, panType } = params
  const qs = new URLSearchParams({ page: String(page), size: String(size) })
  if (status) qs.set('status', status)
  if (panType) qs.set('panType', panType)
  return http.get<ApiPage<TransferJob>>(`/admin/transfer/jobs?${qs}`)
}

export function enqueueJob(body: {
  jobType: string
  panType: string
  shareUrl: string
  sharePwd?: string
  mediaLinkId?: number
  targetFolderId?: string
  priority?: number
}) {
  return http.post<TransferJob>(`/admin/transfer/jobs`, body)
}

export function listMonitors(params: { page?: number; size?: number; status?: string }) {
  const { page = 1, size = 20, status } = params
  const qs = new URLSearchParams({ page: String(page), size: String(size) })
  if (status) qs.set('status', status)
  return http.get<ApiPage<TransferMonitor>>(`/admin/transfer/monitors?${qs}`)
}

export function enableMonitor(body: {
  mediaLinkId: number
  panType: string
  shareUrl: string
  sharePwd?: string
}) {
  return http.post<TransferMonitor>(`/admin/transfer/monitors`, body)
}

export function sweepMonitors() {
  return http.post<{ enqueued: number }>(`/admin/transfer/monitors/sweep`)
}

export function listAccounts() {
  return http.get<TransferAccount[]>(`/admin/transfer/accounts`)
}

/** 夸克/百度：粘贴 cookie 加号（accountName 留空）或换号（填已有号覆盖）。 */
export function addByCookie(body: { panType: string; accountName?: string; cookie: string }) {
  return http.post<LoginSession>(`/admin/transfer/accounts/cookie`, body)
}

/** 迅雷：发起授权，返回 sessionId + 授权链接（浏览器打开登录目标账号并同意）。 */
export function startXunleiAuthorize(body: { accountName?: string }) {
  return http.post<{ sessionId: string; authorizeUrl: string; redirectUri: string }>(
    `/admin/transfer/xunlei/authorize`,
    body,
  )
}

/** 迅雷回调域名不通时的兜底：授权后把地址栏那串（含 code 的完整 URL 或纯 code）贴回来换 token。 */
export function submitXunleiCode(body: { sessionId: string; code: string }) {
  return http.post<LoginSession>(`/admin/transfer/xunlei/code`, body)
}

export function getLoginStatus(sessionId: string) {
  return http.get<LoginSession>(`/admin/transfer/login/${sessionId}`)
}

export interface PanPointer {
  panType: string
  panLabel: string
  followAccountName?: string
  libraryAccountName?: string
  accountNames: string[]
}

export function listPointers() {
  return http.get<PanPointer[]>(`/admin/transfer/pointers`)
}

export function savePointer(body: {
  panType: string
  followAccountName?: string
  libraryAccountName?: string
}) {
  return http.post<PanPointer[]>(`/admin/transfer/pointers`, body)
}

/** 删除账号（通常因封号）：直接删行 + 放弃其名下未删资源。返回放弃记录数。 */
export function deleteAccount(id: number) {
  return http.post<{ abandoned: number }>(`/admin/transfer/accounts/${id}/delete`)
}

/** 百度开放平台隐式授权页地址（浏览器打开授权后复制页面上的 access_token）。 */
export function getBaiduAuthorizeUrl() {
  return http.get<{ authorizeUrl: string }>(`/admin/transfer/baidu/authorize-url`)
}

/** 保存某百度号的删除令牌：粘贴授权页返回的整条 URL / fragment / 纯 access_token 均可。 */
export function setBaiduToken(id: number, token: string) {
  return http.post<void>(`/admin/transfer/accounts/${id}/baidu-token`, { token })
}
