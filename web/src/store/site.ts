import { defineStore } from 'pinia'
import { fetchSiteConfig } from '@/api/site'

export const useSiteStore = defineStore('site', {
  state: () => ({
    xunleiSdkEnabled: false,
    xunleiPartnerCustom: '',
    /** 允许展示的网盘 label；null = 配置未到位，前台默认全显示，避免加载中闪掉页签。 */
    enabledPans: null as string[] | null,
    noticeEnabled: false,
    noticeTitle: '网站公告',
    noticeContent: '',
    noticeShowOnce: false,
    contactEnabled: false,
    contactTitle: '防止失联',
    contactTip: '',
    contactGroupQrcode: '',
    contactMpQrcode: '',
    loaded: false,
  }),

  getters: {
    /** SDK 已开启且配置了达人数字账号（结算必填）。 */
    thunderReady(s): boolean {
      return s.xunleiSdkEnabled && !!s.xunleiPartnerCustom.trim()
    },
    /** 判断某网盘是否允许展示（配置未到位时一律显示）。 */
    isPanVisible(s) {
      return (label: string): boolean => s.enabledPans == null || s.enabledPans.includes(label)
    },
    /** 站内是否展示加群入口（至少有一张码）。 */
    contactReady(s): boolean {
      return (
        s.contactEnabled &&
        (!!s.contactGroupQrcode.trim() || !!s.contactMpQrcode.trim())
      )
    },
  },

  actions: {
    async init() {
      try {
        const cfg = await fetchSiteConfig()
        this.xunleiSdkEnabled = cfg.xunleiSdkEnabled
        this.xunleiPartnerCustom = cfg.xunleiPartnerCustom?.trim() || ''
        this.enabledPans = Array.isArray(cfg.enabledPans) ? cfg.enabledPans : null
        this.noticeEnabled = !!cfg.noticeEnabled
        this.noticeTitle = (cfg.noticeTitle || '').trim() || '网站公告'
        this.noticeContent = (cfg.noticeContent || '').trim()
        this.noticeShowOnce = !!cfg.noticeShowOnce
        this.contactEnabled = !!cfg.contactEnabled
        this.contactTitle = (cfg.contactTitle || '').trim() || '防止失联'
        this.contactTip = (cfg.contactTip || '').trim()
        this.contactGroupQrcode = (cfg.contactGroupQrcode || '').trim()
        this.contactMpQrcode = (cfg.contactMpQrcode || '').trim()
      } catch {
        this.xunleiSdkEnabled = false
        this.xunleiPartnerCustom = ''
        this.enabledPans = null
        this.noticeEnabled = false
        this.noticeTitle = '网站公告'
        this.noticeContent = ''
        this.noticeShowOnce = false
        this.contactEnabled = false
        this.contactTitle = '防止失联'
        this.contactTip = ''
        this.contactGroupQrcode = ''
        this.contactMpQrcode = ''
      } finally {
        this.loaded = true
      }
    },
  },
})
