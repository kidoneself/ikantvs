import type { PanType } from '@/data/mock'

const SDK_URL = 'https://open.thunderurl.com/thunder-link.js'

export interface ThunderTask {
  url: string
  name?: string
  size?: number
}

export interface ThunderNewTaskOptions {
  downloadDir?: string
  taskGroupName?: string
  tasks: ThunderTask[]
  extra: { custom: string }
}

declare global {
  interface Window {
    thunderLink?: {
      newTask(options: ThunderNewTaskOptions): void
    }
  }
}

let sdkPromise: Promise<void> | null = null

/** 动态加载迅雷官方 JS-SDK（CDN）。 */
export function ensureThunderSdk(): Promise<void> {
  if (window.thunderLink?.newTask) return Promise.resolve()
  if (sdkPromise) return sdkPromise
  sdkPromise = new Promise((resolve, reject) => {
    const existing = document.querySelector(`script[src="${SDK_URL}"]`)
    if (existing) {
      if (window.thunderLink?.newTask) {
        resolve()
        return
      }
      existing.addEventListener('load', () => resolve(), { once: true })
      existing.addEventListener('error', () => reject(new Error('迅雷 SDK 加载失败')), { once: true })
      return
    }
    const script = document.createElement('script')
    script.src = SDK_URL
    script.async = true
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('迅雷 SDK 加载失败'))
    document.head.appendChild(script)
  })
  return sdkPromise
}

/** 磁力 / 迅雷链适合走 JS-SDK。 */
export function isThunderDownloadable(pan: PanType, url?: string): boolean {
  if (!url?.trim()) return false
  const lower = url.trim().toLowerCase()
  if (pan === '磁力' || lower.startsWith('magnet:')) return true
  if (pan === '迅雷') return true
  return false
}

function sanitizeFileName(raw: string): string {
  return raw.replace(/[\\/:*?"<>|]/g, '_').slice(0, 120)
}

/** 供迅雷任务面板展示的文件名。 */
export function thunderTaskName(mediaTitle: string, note?: string): string {
  const base = note?.trim() || mediaTitle.trim() || '资源'
  const name = sanitizeFileName(base)
  if (/\.[a-z0-9]{2,5}$/i.test(name)) return name
  return `${name}.mkv`
}

export async function thunderDownload(params: {
  url: string
  mediaTitle: string
  note?: string
  partnerCustom: string
  downloadDir?: string
}): Promise<void> {
  const custom = params.partnerCustom.trim()
  if (!custom) throw new Error('迅雷推广账号未配置')

  await ensureThunderSdk()
  if (!window.thunderLink?.newTask) throw new Error('迅雷 SDK 不可用')

  window.thunderLink.newTask({
    downloadDir: sanitizeFileName(params.downloadDir || params.mediaTitle || '爱看'),
    tasks: [{
      url: params.url.trim(),
      name: thunderTaskName(params.mediaTitle, params.note),
    }],
    extra: { custom },
  })
}
