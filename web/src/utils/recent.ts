/** 最近搜索（本地存储，设计阶段无需后端） */
const KEY = 'jyinshi_recent_search'
const MAX = 8

export function getRecent(): string[] {
  try {
    const raw = localStorage.getItem(KEY)
    return raw ? (JSON.parse(raw) as string[]) : []
  } catch {
    return []
  }
}

export function addRecent(keyword: string) {
  const kw = keyword.trim()
  if (!kw) return
  const list = getRecent().filter((x) => x !== kw)
  list.unshift(kw)
  localStorage.setItem(KEY, JSON.stringify(list.slice(0, MAX)))
}

export function clearRecent() {
  localStorage.removeItem(KEY)
}
