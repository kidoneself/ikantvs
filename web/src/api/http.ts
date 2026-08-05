/**
 * 轻量 HTTP 封装（基于 fetch，零依赖）。
 *
 * 约定：后端统一返回 { code, message, data }，code===0 为成功。
 */
import { onApiPerfEnd, onApiPerfStart } from '@/lib/pagePerf'
import { getVisitorId } from '@/lib/visitor'

const BASE = (import.meta.env.VITE_API_BASE as string) || '/api'
export const TOKEN_KEY = 'jyinshi_token'

export interface ApiError extends Error {
  code: number
}

function makeError(code: number, message: string): ApiError {
  const e = new Error(message) as ApiError
  e.code = code
  return e
}

interface RequestOptions {
  method?: string
  body?: unknown
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body } = options
  const headers: Record<string, string> = { 'X-Visitor-Id': getVisitorId() }
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  onApiPerfStart()
  const t0 = performance.now()
  try {
    const resp = await fetch(BASE + path, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    })

    if (!resp.ok) {
      throw makeError(resp.status, `请求失败 (${resp.status})`)
    }

    let json: { code: number; message: string; data: T }
    try {
      json = await resp.json()
    } catch {
      throw makeError(-1, '响应解析失败')
    }

    if (json.code !== 0) {
      throw makeError(json.code, json.message || '操作失败')
    }

    return json.data
  } catch (e) {
    if (e instanceof Error && 'code' in e) throw e
    throw makeError(-1, '网络异常，请稍后重试')
  } finally {
    onApiPerfEnd(performance.now() - t0)
  }
}

export const http = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) => request<T>(path, { method: 'POST', body }),
  put: <T>(path: string, body?: unknown) => request<T>(path, { method: 'PUT', body }),
  del: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
}
