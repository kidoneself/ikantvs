/**
 * 转存 API：搜索结果点「转存」把网盘分享转到平台账号，拿回我方长效分享链。
 * 异步：execute 命中缓存直接 done，否则 transferring + jobId，前端轮询 result。
 */
import { http } from '@/api/http'
import type { PanType } from '@/data/mock'

export type TransferStatus = 'transferring' | 'done' | 'failed'

export interface TransferResult {
  status: TransferStatus
  jobId?: number
  shareUrl?: string
  password?: string
  message?: string
}

/** 前端网盘标签 → 后端/worker 支持的 panType 代码；不支持的返回 null（不显示转存按钮）。 */
const PAN_LABEL_TO_CODE: Record<string, string> = {
  夸克: 'quark',
  百度: 'baidu',
  迅雷: 'xunlei',
  // 搜索页展示名 / 代码，避免误传后按钮全灰
  夸克APP: 'quark',
  夸克网盘: 'quark',
  百度网盘: 'baidu',
  迅雷云盘: 'xunlei',
  迅雷网盘: 'xunlei',
  quark: 'quark',
  baidu: 'baidu',
  xunlei: 'xunlei',
}

export function panCodeFor(label: PanType | string): string | null {
  return PAN_LABEL_TO_CODE[label] ?? null
}

export function canTransfer(label: PanType | string): boolean {
  return panCodeFor(label) != null
}

/**
 * 触发转存：站内链传 mediaLinkId；流式搜索外源传 encryptUrl。
 */
export async function executeTransfer(params: {
  mediaLinkId?: number
  encryptUrl?: string
}): Promise<TransferResult> {
  return http.post<TransferResult>('/transfer/execute', params)
}

export async function fetchTransferResult(jobId: number): Promise<TransferResult> {
  return http.get<TransferResult>(`/transfer/result?jobId=${jobId}`)
}
