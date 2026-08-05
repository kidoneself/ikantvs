import { http } from './http'

/**
 * 每日更新（content 域 · 策展看板）。
 *
 * 一条 = 运营选一部剧 + 录入上游分享链；系统用该盘监控号做监控转存（创建/更新），
 * 生成稳定我方分享链回写前台。账号不选手动——每盘只有一套 monitor 号。
 */

/** 录入/编辑时的上游链输入（一部剧可填多套：夸克/百度/备用）。 */
export interface DailyLinkInput {
  /** quark/baidu/xunlei */
  panType: string
  /** 上游大佬分享链（被监控转存的源） */
  shareUrl: string
  sharePwd?: string
}

/** 某条监控链的运行态（展示用，来自 transfer 域回写）。 */
export interface DailyMonitorView {
  panType: string
  /** 上游源分享链 */
  shareUrl?: string
  /** 实际落在哪个监控号（只读展示） */
  accountName?: string
  /** active/invalid/paused */
  status?: string
  /** 我方分享链（创建后生成，用户看/点这个） */
  myShareUrl?: string
  /** 该盘最新集数/日期 */
  latestEpisode?: string
}

export interface DailyItem {
  id: number
  mediaId: number
  /** 以下 media 字段由后端 join 带出 */
  title?: string
  poster?: string
  type?: string
  year?: number
  /** 展示：更新至第 X 集 / 日期（手动值与各盘自动值取较新） */
  latestEpisode?: string
  /** 运营手动填写的集数/日期（空=纯自动） */
  manualEpisode?: string
  /** 最近一次真正补到新集数的时间（"更新了没"看这个） */
  lastUpdateAt?: string
  /** 最近一次巡检检查的时间 */
  lastCheckAt?: string
  /** 整体状态：ended 已完结 / active 正常追更 / invalid 源失效 / paused 暂停 / none 未建追更 */
  status?: string
  /** 追更节奏 · 检查日 0-6（周日-周六）逗号分隔；空=每天 */
  checkDays?: string
  /** 追更节奏 · 检查时段 "起-止"（止不含），如 18-23；空=用全局巡检 */
  checkHours?: string
  /** 追更节奏 · 检查间隔（分钟）；空=用全局巡检 */
  checkInterval?: number
  /** 1=置顶 */
  pinned: number
  sort: number
  /** 1=上架（前台可见） */
  enabled: number
  /** 1=已完结（停追更，换号不迁） */
  ended?: number
  /** 各盘追更链状态 */
  monitors?: DailyMonitorView[]
}

export interface DailySaveBody {
  id?: number
  /** 已有剧；与 title 二选一 */
  mediaId?: number
  /** 库没有时用片名新建 */
  title?: string
  type?: string
  year?: number
  pinned?: number
  sort?: number
  enabled?: number
  /** 上游链；后端据此建/改监控转存 */
  links?: DailyLinkInput[]
  /** 追更节奏 · 检查日 0-6（周日-周六）逗号分隔；空=每天 */
  checkDays?: string
  /** 追更节奏 · 检查时段 "起-止"（止不含），如 18-23；空=用全局巡检 */
  checkHours?: string
  /** 追更节奏 · 检查间隔（分钟）；空=用全局巡检 */
  checkInterval?: number
}

interface ApiPage<T> {
  total: number
  page: number
  size: number
  records: T[]
}

export function fetchDailyList(params: {
  page?: number
  size?: number
  keyword?: string
  /** 0=未完结 1=已完结，不传=全部 */
  ended?: number
}) {
  const { page = 1, size = 20, keyword, ended } = params
  const qs = new URLSearchParams({ page: String(page), size: String(size) })
  if (keyword?.trim()) qs.set('keyword', keyword.trim())
  if (ended === 0 || ended === 1) qs.set('ended', String(ended))
  return http.get<ApiPage<DailyItem>>(`/admin/daily?${qs}`)
}

export function fetchDaily(id: number) {
  return http.get<DailyItem>(`/admin/daily/${id}`)
}

export function saveDaily(body: DailySaveBody) {
  return http.post<DailyItem>('/admin/daily', body)
}

export function deleteDaily(id: number) {
  return http.del<void>(`/admin/daily/${id}`)
}

/** 立即检查该剧追更（无视时段补一轮 probe），返回入队条数。 */
export function checkDaily(id: number) {
  return http.post<{ enqueued: number }>(`/admin/daily/${id}/check`, {})
}

/** 快捷改：上架 / 置顶 / 排序 / 完结 / 手动集数（manualEpisode 传空串=清除手动值回到自动）。 */
export function patchDaily(
  id: number,
  body: {
    enabled?: number
    pinned?: number
    sort?: number
    manualEpisode?: string
    /** 1=标完结停追更；0=取消完结恢复追更 */
    ended?: number
  },
) {
  return http.put<DailyItem>(`/admin/daily/${id}`, body)
}
