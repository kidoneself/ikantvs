import { reactive } from 'vue'

export interface PagePerfStats {
  backend: number | null
  vue: number | null
  network: number | null
}

export const pagePerf = reactive<PagePerfStats>({
  backend: null,
  vue: null,
  network: null,
})

const perfCache = new Map<string, PagePerfStats>()

let routeKey = ''
let routeT0 = 0
let apiWallStart = 0
let apiWallEnd = 0
let pendingApi = 0
let apiRoundTrips: number[] = []
let readyTimer = 0
let routeIdleTimer = 0
let finished = false

function applyStats(stats: PagePerfStats) {
  pagePerf.backend = stats.backend
  pagePerf.vue = stats.vue
  pagePerf.network = stats.network
}

function resetRoutePerf(key: string) {
  routeKey = key
  routeT0 = performance.now()
  apiWallStart = 0
  apiWallEnd = 0
  pendingApi = 0
  apiRoundTrips = []
  finished = false
  pagePerf.backend = null
  pagePerf.vue = null
  pagePerf.network = null
  window.clearTimeout(readyTimer)
  window.clearTimeout(routeIdleTimer)
}

/** 从 PerformanceResourceTiming 汇总本页加载的资源耗时 */
function sumResourceTimingSince(since: number) {
  let apiBackend = 0
  let apiNetwork = 0
  let otherNetwork = 0

  for (const e of performance.getEntriesByType('resource') as PerformanceResourceTiming[]) {
    if (e.responseEnd <= since) continue
    const isApi = e.name.includes('/api/')
    if (isApi) {
      if (e.responseStart > 0 && e.requestStart > 0) {
        apiBackend += e.responseStart - e.requestStart
        apiNetwork += Math.max(0, e.responseEnd - e.responseStart)
      } else if (e.duration > 0) {
        // 跨域无 TAO 时只有 duration，粗分：少量算后端，其余算网络
        apiBackend += e.duration * 0.15
        apiNetwork += e.duration * 0.85
      }
    } else if (['fetch', 'xmlhttprequest', 'script', 'link', 'css', 'img', 'font'].includes(e.initiatorType)) {
      otherNetwork += e.duration
    }
  }

  return { apiBackend, apiNetwork, otherNetwork }
}

function finishRoutePerf() {
  if (finished || routeT0 <= 0) return
  finished = true

  const end = performance.now()
  const resource = sumResourceTimingSince(routeT0)

  let backend = apiWallStart > 0 ? apiWallEnd - apiWallStart : 0
  if (apiRoundTrips.length) {
    backend = Math.max(backend, ...apiRoundTrips)
  }
  backend = Math.max(backend, resource.apiBackend)

  let network = resource.apiNetwork + resource.otherNetwork
  const total = end - routeT0
  const vue = Math.max(0, total - backend - network)

  const stats: PagePerfStats = { backend, vue, network }
  applyStats(stats)
  if (routeKey) perfCache.set(routeKey, { ...stats })
}

function scheduleFinish() {
  window.clearTimeout(readyTimer)
  readyTimer = window.setTimeout(() => finishRoutePerf(), 48)
}

export function onRoutePerfStart(key: string, keepAliveRevisit = false) {
  if (keepAliveRevisit) {
    const cached = perfCache.get(key)
    if (cached) {
      routeKey = key
      finished = true
      applyStats(cached)
      return
    }
  }
  resetRoutePerf(key)
  routeIdleTimer = window.setTimeout(() => {
    if (pendingApi === 0) scheduleFinish()
  }, 200)
}

export function onApiPerfStart() {
  const now = performance.now()
  if (!apiWallStart) apiWallStart = now
  pendingApi++
}

export function onApiPerfEnd(durationMs: number) {
  apiRoundTrips.push(durationMs)
  apiWallEnd = performance.now()
  pendingApi = Math.max(0, pendingApi - 1)
  if (pendingApi === 0) scheduleFinish()
}

export function markPagePerfReady() {
  scheduleFinish()
}

export function formatPerfMs(ms: number | null): string {
  if (ms == null) return '—'
  if (ms < 10) return ms.toFixed(1)
  return String(Math.round(ms))
}
