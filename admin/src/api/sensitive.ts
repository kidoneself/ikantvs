import { http } from './http'

export interface SensitiveWord {
  id: number
  word: string
  category: string
  action: string
  enabled: boolean
  remark?: string
  createdAt?: string
  updatedAt?: string
}

export interface SensitiveCheckResult {
  hit: boolean
  words: string[]
  action: string | null
  blocked: boolean
  filtered: string
}

interface ApiPage<T> {
  total: number
  page: number
  size: number
  records: T[]
}

export const CATEGORY_LABEL: Record<string, string> = {
  politics: '政治',
  porn: '色情',
  ad: '广告导流',
  violence: '暴恐',
  legacy: '历史迁入',
  other: '其他',
}

export const ACTION_LABEL: Record<string, string> = {
  block: '拦截',
  review: '转审核',
  replace: '打码',
  warn: '仅标记',
}

export const ACTION_TYPE: Record<string, '' | 'success' | 'warning' | 'info' | 'danger'> = {
  block: 'danger',
  review: 'warning',
  replace: 'info',
  warn: '',
}

export function fetchMeta() {
  return http.get<{ categories: string[]; actions: string[] }>(`/admin/sensitive/meta`)
}

export function fetchWords(params: {
  page?: number
  size?: number
  category?: string
  action?: string
  keyword?: string
}) {
  const { page = 1, size = 20, category, action, keyword } = params
  const qs = new URLSearchParams({ page: String(page), size: String(size) })
  if (category) qs.set('category', category)
  if (action) qs.set('action', action)
  if (keyword?.trim()) qs.set('keyword', keyword.trim())
  return http.get<ApiPage<SensitiveWord>>(`/admin/sensitive?${qs}`)
}

export function createWord(body: Partial<SensitiveWord>) {
  return http.post<SensitiveWord>(`/admin/sensitive`, body)
}

export function updateWord(id: number, body: Partial<SensitiveWord>) {
  return http.put<SensitiveWord>(`/admin/sensitive/${id}`, body)
}

export function deleteWord(id: number) {
  return http.del<void>(`/admin/sensitive/${id}`)
}

/** 批量导入：一行一词，整批共用 category/action，返回新增条数。 */
export function importBatch(body: { text: string; category?: string; action?: string }) {
  return http.post<number>(`/admin/sensitive/batch`, body)
}

export function testText(text: string) {
  return http.post<SensitiveCheckResult>(`/admin/sensitive/test`, { text })
}
