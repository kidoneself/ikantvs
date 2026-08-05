/**
 * 匿名访客标识：首次访问生成一个持久 UUID 存 localStorage，
 * 随请求头 X-Visitor-Id 上报，用于独立访客统计。
 */
const KEY = 'jyinshi_vid'

function uuid(): string {
  try {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID()
    }
  } catch {
    /* 部分环境 crypto 不可用，走下方兜底 */
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

let cached: string | null = null

/** 取当前访客 id；不存在则生成并持久化。localStorage 不可用时退化为内存态。 */
export function getVisitorId(): string {
  if (cached) return cached
  try {
    const saved = localStorage.getItem(KEY)
    if (saved) {
      cached = saved
      return saved
    }
    const id = uuid()
    localStorage.setItem(KEY, id)
    cached = id
    return id
  } catch {
    cached = cached || uuid()
    return cached
  }
}
