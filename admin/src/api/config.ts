import { http } from './http'

export interface SysConfigItem {
  key: string
  value: string
  label: string
  group: string
  /** ENUM / BOOL / NUMBER / TEXT / TEXTAREA / SECRET */
  type: string
  options?: string[]
  description?: string
}

export function fetchConfig() {
  return http.get<SysConfigItem[]>('/admin/config')
}

export function updateConfig(values: Record<string, string>) {
  return http.put<SysConfigItem[]>('/admin/config', { values })
}

/** 运营图片上传（本地磁盘 → /api/uploads/...）。 */
export async function uploadAdminImage(file: File, scene = 'notice'): Promise<string> {
  const BASE = (import.meta.env.VITE_API_BASE as string) || '/api'
  const token = localStorage.getItem('jyinshi_admin_token')
  const fd = new FormData()
  fd.append('file', file)
  fd.append('scene', scene)
  const headers: Record<string, string> = {}
  if (token) headers.Authorization = `Bearer ${token}`
  const resp = await fetch(`${BASE}/admin/upload`, { method: 'POST', headers, body: fd })
  const json = await resp.json().catch(() => ({ code: -1, message: '响应解析失败' }))
  if (json.code !== 0) {
    throw new Error(json.message || '上传失败')
  }
  return json.data as string
}
