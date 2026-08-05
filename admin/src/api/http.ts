import router from '@/router'

const BASE = (import.meta.env.VITE_API_BASE as string) || '/api'
export const TOKEN_KEY = 'jyinshi_admin_token'

export interface ApiError extends Error {
  code: number
}

function makeError(code: number, message: string): ApiError {
  const e = new Error(message) as ApiError
  e.code = code
  return e
}

async function request<T>(path: string, method: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) headers['Authorization'] = `Bearer ${token}`

  const resp = await fetch(BASE + path, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })

  // 滑动续期：后端返回新 token 时静默替换
  const refreshed = resp.headers.get('X-Refresh-Token')
  if (refreshed) localStorage.setItem(TOKEN_KEY, refreshed)

  const json = await resp.json().catch(() => ({ code: -1, message: '响应解析失败' }))
  if (json.code !== 0) {
    if (json.code === 401) {
      localStorage.removeItem(TOKEN_KEY)
      if (router.currentRoute.value.name !== 'login') {
        router.replace({ name: 'login' })
      }
    }
    throw makeError(json.code, json.message || '操作失败')
  }
  return json.data as T
}

export const http = {
  get: <T>(path: string) => request<T>(path, 'GET'),
  post: <T>(path: string, body?: unknown) => request<T>(path, 'POST', body),
  put: <T>(path: string, body?: unknown) => request<T>(path, 'PUT', body),
  del: <T>(path: string) => request<T>(path, 'DELETE'),
}
