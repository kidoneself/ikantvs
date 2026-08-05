/**
 * 流式搜索（对齐老站）：GET /api/search/stream?kw=  SSE 推送。
 * 一次返回全盘，前端按 Tab 本地筛选；不传 cloudTypes。
 */

export interface StreamSearchItem {
  title: string
  /** 加密 token（网盘）或明文（磁力/电驴/自营）。 */
  url?: string | null
  cloudType: string
  invalid?: boolean
  local?: boolean
  latestEpisode?: string | null
  mediaLinkId?: number | null
  mediaId?: number | null
}

export interface StreamSearchEvent {
  type: 'start' | 'item' | 'items' | 'complete' | 'error'
  source?: string
  item?: StreamSearchItem
  /** 批量推送（后端加速用，优先于单条 item） */
  items?: StreamSearchItem[]
  message?: string
  error?: string
  progress?: number
}

/** 列表展示用（由 SSE item 累积）。 */
export interface SearchLinkItem {
  /** 前端列表 key；站内链用 mediaLinkId，外源用加密 url。 */
  key: string
  id?: number
  title: string
  panType: string
  panLabel: string
  source?: string
  mediaId?: number
  mediaTitle?: string
  local?: boolean
  url?: string | null
  /** 外源加密 token，转存时传 encryptUrl。 */
  encryptUrl?: string | null
  invalid?: boolean
  latestEpisode?: string | null
  _seq?: number
}

/**
 * SSE 流式搜索。返回 EventSource，调用方负责 close。
 */
export function streamSearch(
  params: { kw: string; cloudTypes?: string; refresh?: boolean },
  onMessage: (event: StreamSearchEvent) => void,
  onError?: (err: unknown) => void,
  onComplete?: () => void,
): EventSource {
  const qs = new URLSearchParams()
  qs.set('kw', params.kw)
  if (params.cloudTypes) qs.set('cloudTypes', params.cloudTypes)
  if (params.refresh !== undefined) qs.set('refresh', String(params.refresh))

  const base = (import.meta.env.VITE_API_BASE as string | undefined) || '/api'
  const url = `${base.replace(/\/$/, '')}/search/stream?${qs}`

  const es = new EventSource(url)

  es.addEventListener('message', (ev) => {
    try {
      const data = JSON.parse((ev as MessageEvent).data) as StreamSearchEvent
      onMessage(data)
      if (data.type === 'complete') {
        es.close()
        onComplete?.()
      }
    } catch (e) {
      onError?.(e)
    }
  })

  es.onerror = () => {
    es.close()
    onMessage({ type: 'error', error: '搜索连接中断，请重试' })
    onComplete?.()
  }

  return es
}
