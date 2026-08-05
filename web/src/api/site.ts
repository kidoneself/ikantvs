import { http } from '@/api/http'

export interface SiteConfig {
  xunleiSdkEnabled: boolean
  xunleiPartnerCustom: string
  /** 允许展示的网盘类型 label；后端未返回时前台默认全显示。 */
  enabledPans?: string[]
  /** 网站公告 */
  noticeEnabled?: boolean
  noticeTitle?: string
  noticeContent?: string
  noticeShowOnce?: boolean
  /** 站内加群 / 联系（与活码共用） */
  contactEnabled?: boolean
  contactTitle?: string
  contactTip?: string
  contactGroupQrcode?: string
  contactMpQrcode?: string
}

export function fetchSiteConfig() {
  return http.get<SiteConfig>('/site/config')
}

export interface LiveQrPage {
  qrcodeImage: string
  mpQrcodeImage: string
  title: string
  tipText: string
  scanCount: number
}

export function fetchLiveQrPage(from?: string) {
  const q = from ? `?from=${encodeURIComponent(from)}` : ''
  return http.get<LiveQrPage>(`/qr${q}`)
}
