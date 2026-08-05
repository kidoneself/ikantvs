<script setup lang="ts">
/**
 * 转存结果弹窗：详情页点资源 → 转存到本站网盘 → 在此展示我方长效分享链。
 *
 * 取代老实现里转存成功后 window.open 自动开标签的做法（异步回调里的 window.open
 * 会被浏览器判为弹窗拦截，用户点了没反应）。改为在弹窗内就地展示链接 + 提取码，
 * 「打开网盘 / 复制链接」都由用户真实点击触发，不会被拦。
 */
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import type { PanType } from '@/data/mock'

type TransferDialogStatus = 'loading' | 'done' | 'failed'

const props = defineProps<{
  visible: boolean
  status: TransferDialogStatus
  pan?: PanType
  /** 资源原始标题，用于弹窗头部副标题 */
  title?: string
  /** 转存成功后我方分享链 */
  shareUrl?: string
  /** 我方分享链提取码 */
  password?: string
  /** 失败原因（后端原始消息） */
  message?: string
  /** 失败时「去全网搜」用的关键词（通常是剧名） */
  keyword?: string
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'retry'): void
  (e: 'search'): void
}>()

interface PanInfo {
  label: string
  color: string
  letter: string
  appName: string
  qrTip: string
}

const PAN_INFO: Record<string, PanInfo> = {
  夸克: {
    label: '夸克网盘', color: 'var(--pan-quark)', letter: '夸', appName: '夸克 APP',
    qrTip: '打开夸克 APP → 顶部搜索框右侧相机图标 → 扫一扫',
  },
  百度: {
    label: '百度网盘', color: 'var(--pan-baidu)', letter: '度', appName: '百度网盘 APP',
    qrTip: '打开百度网盘 APP → 右上角「+」→ 扫一扫',
  },
  迅雷: {
    label: '迅雷网盘', color: 'var(--pan-xunlei)', letter: '迅', appName: '迅雷 APP',
    qrTip: '打开迅雷 APP → 扫一扫',
  },
}

const info = computed<PanInfo>(
  () =>
    PAN_INFO[props.pan ?? ''] ?? {
      label: '网盘资源', color: 'var(--text-muted)', letter: '盘', appName: '对应 APP',
      qrTip: '打开对应 APP → 扫一扫',
    },
)

/** 我方分享链（把提取码拼进去，方便一次复制/扫码直达）。 */
const fullUrl = computed(() => {
  const u = (props.shareUrl || '').trim()
  const pwd = (props.password || '').trim()
  if (!u) return ''
  if (pwd && !/[?&]pwd=/i.test(u)) {
    return u + (u.includes('?') ? '&' : '?') + 'pwd=' + pwd
  }
  return u
})

/* ----------------------------- 错误分类 ----------------------------- */

type ErrorKind = 'timeout' | 'rate-limit' | 'expired' | 'unknown'

const errorKind = computed<ErrorKind>(() => {
  const e = props.message || ''
  if (e.includes('超时') || e.includes('网络')) return 'timeout'
  if (
    e.includes('超限') || e.includes('转存数量') || e.includes('转存次数') ||
    e.includes('频繁') || e.includes('Cookie') || e.includes('cookie') ||
    e.includes('登录状态失效') || e.includes('安全验证') || e.includes('提取码验证失败')
  ) {
    return 'rate-limit'
  }
  if (
    e.includes('已失效') || e.includes('已删除') || e.includes('已过期') ||
    e.includes('已取消') || e.includes('分享已') || e.includes('不存在') ||
    e.includes('没有访问权限')
  ) {
    return 'expired'
  }
  return 'unknown'
})

const errorTitle = computed(() => {
  switch (errorKind.value) {
    case 'timeout': return '转存超时'
    case 'rate-limit': return '当前资源紧张'
    default: return '资源已失效'
  }
})

const errorEmoji = computed(() => {
  switch (errorKind.value) {
    case 'timeout': return '⏳'
    case 'rate-limit': return '🚦'
    default: return '😅'
  }
})

const errorHint = computed(() => {
  switch (errorKind.value) {
    case 'rate-limit': return '本站短时间转存量过大触发了网盘限流，过几分钟再试即可'
    case 'timeout': return ''
    default: return '资源失效是正常现象，热门剧容易被和谐，换一个一般都能找到'
  }
})

const canRetry = computed(() => errorKind.value === 'timeout' || errorKind.value === 'rate-limit')

/* ------------------------------ 复制 ------------------------------- */

const copied = ref(false)
let copyTimer: ReturnType<typeof setTimeout> | null = null

async function copyLink() {
  const text = fullUrl.value
  if (!text) return
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
    } else {
      const ta = document.createElement('textarea')
      ta.value = text
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    }
    copied.value = true
    if (copyTimer) clearTimeout(copyTimer)
    copyTimer = setTimeout(() => (copied.value = false), 1500)
  } catch {
    /* 复制失败静默，用户仍可手动选中链接文本 */
  }
}

/* ------------------------------ 二维码 ----------------------------- */

const isMobile = ref(false)
function checkDevice() {
  if (typeof window === 'undefined') return
  isMobile.value = window.matchMedia('(max-width: 1024px)').matches
}

const qrcodeRef = ref<HTMLElement | null>(null)

async function generateQR() {
  const el = qrcodeRef.value
  if (!el || !fullUrl.value || isMobile.value) return
  try {
    const { default: QRCode } = await import('qrcode')
    const canvas = await QRCode.toCanvas(fullUrl.value, {
      width: 132,
      margin: 1,
      errorCorrectionLevel: 'M',
    })
    el.innerHTML = ''
    el.appendChild(canvas)
  } catch {
    /* 二维码是增强项，失败不影响链接展示 */
  }
}

/* --------------------------- 生命周期 ------------------------------ */

function onEsc(e: KeyboardEvent) {
  if (e.key === 'Escape') emit('close')
}

watch(
  () => props.visible,
  (v) => {
    if (typeof document === 'undefined') return
    if (v) {
      checkDevice()
      document.body.style.overflow = 'hidden'
      document.addEventListener('keydown', onEsc)
      window.addEventListener('resize', checkDevice)
    } else {
      document.body.style.overflow = ''
      document.removeEventListener('keydown', onEsc)
      window.removeEventListener('resize', checkDevice)
      copied.value = false
    }
  },
)

// 成功态且拿到链接时（PC）生成二维码
watch(
  () => [props.visible, props.status, fullUrl.value] as const,
  async ([visible, status]) => {
    if (visible && status === 'done' && fullUrl.value && !isMobile.value) {
      await nextTick()
      setTimeout(generateQR, 60)
    }
  },
)

onUnmounted(() => {
  if (copyTimer) clearTimeout(copyTimer)
  if (typeof document !== 'undefined') {
    document.removeEventListener('keydown', onEsc)
    document.body.style.overflow = ''
  }
  if (typeof window !== 'undefined') {
    window.removeEventListener('resize', checkDevice)
  }
})
</script>

<template>
  <Teleport to="body">
    <Transition name="t-fade">
      <div v-if="visible" class="t-overlay" role="dialog" aria-modal="true" @click.self="emit('close')">
        <Transition name="t-zoom" appear>
          <div v-if="visible" class="t-box" @click.stop>
            <button class="t-close" type="button" aria-label="关闭" @click="emit('close')">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                <line x1="6" y1="6" x2="18" y2="18" />
                <line x1="18" y1="6" x2="6" y2="18" />
              </svg>
            </button>

            <div class="t-header">
              <span class="t-pan-badge" :style="{ background: info.color }">{{ info.letter }}</span>
              <div class="t-header-text">
                <div class="t-pan-label">{{ info.label }}</div>
                <div class="t-title">{{ title || '资源转存' }}</div>
              </div>
            </div>

            <div class="t-body">
              <!-- 加载中 -->
              <div v-if="status === 'loading'" class="t-state t-state-loading">
                <div class="t-spinner" />
                <p class="t-state-title">正在转存到本站网盘…</p>
                <p class="t-state-sub">本站正在为你保存并生成新链接，请稍候</p>
              </div>

              <!-- 失败 -->
              <div v-else-if="status === 'failed'" class="t-state t-state-error">
                <div class="t-emoji">{{ errorEmoji }}</div>
                <p class="t-state-title">{{ errorTitle }}</p>
                <p v-if="errorHint" class="t-state-hint">{{ errorHint }}</p>
                <div class="t-actions">
                  <button v-if="canRetry" class="t-btn t-btn-primary" type="button" @click="emit('retry')">重新尝试</button>
                  <button v-else class="t-btn t-btn-primary" type="button" @click="emit('close')">换一个试试</button>
                  <button v-if="keyword" class="t-btn t-btn-ghost" type="button" @click="emit('search')">
                    去全网搜「{{ keyword }}」
                  </button>
                </div>
              </div>

              <!-- 成功 -->
              <div v-else class="t-state t-state-success">
                <div class="t-source-tag">
                  <span class="t-source-icon">✓</span>
                  已为你转存到本站网盘，链接稳定可用
                </div>

                <!-- PC 端：二维码，扫码在手机 APP 直接保存 -->
                <div v-if="!isMobile && fullUrl" class="t-qr-section">
                  <div class="t-qr-title">用<span>{{ info.appName }}</span>扫码直接保存</div>
                  <div class="t-qr-tip">{{ info.qrTip }}</div>
                  <div ref="qrcodeRef" class="t-qr-box" />
                </div>

                <!-- 手机端：直接给一个打开网盘 APP 保存的大按钮（扫自己屏幕无意义） -->
                <a
                  v-else-if="isMobile && fullUrl"
                  class="t-open-btn"
                  :href="fullUrl"
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  打开{{ info.appName }}保存资源
                </a>

                <div class="t-tips">
                  <div class="t-tip t-tip-warn">
                    <span class="t-tip-icon">⚠️</span>
                    <span><strong>必须先点【保存到我的网盘】</strong>，再下载或观看</span>
                  </div>
                  <div v-if="pan === '夸克'" class="t-tip t-tip-soft">
                    <span class="t-tip-icon">💡</span>
                    <span>夸克和谐较快，若失效可切换百度网盘版本</span>
                  </div>
                </div>

                <div class="t-link-block">
                  <div class="t-row-label">
                    <span>资源地址（点击直接打开）</span>
                    <button class="t-mini-btn" type="button" @click="copyLink">
                      {{ copied ? '✓ 已复制' : '复制链接' }}
                    </button>
                  </div>
                  <a class="t-link" :href="fullUrl" target="_blank" rel="noopener noreferrer">{{ fullUrl }}</a>
                  <p v-if="password" class="t-pwd">提取码：<strong>{{ password }}</strong></p>
                </div>
              </div>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped lang="scss">
.t-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  -webkit-backdrop-filter: blur(4px);
  backdrop-filter: blur(4px);
}
.t-box {
  position: relative;
  width: 100%;
  max-width: 440px;
  max-height: calc(100vh - 48px);
  overflow-y: auto;
  background: var(--surface);
  border-radius: var(--radius-l);
  box-shadow: var(--shadow-hover);
}
.t-close {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  border-radius: var(--radius-s);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 2;
  &:hover {
    background: var(--surface-2);
    color: var(--text);
  }
}
.t-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 20px 14px;
  border-bottom: 1px solid var(--border);
}
.t-pan-badge {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: var(--radius-s);
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.t-header-text {
  min-width: 0;
}
.t-pan-label {
  font-size: 12px;
  color: var(--text-muted);
}
.t-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text);
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.t-body {
  padding: 16px 20px 20px;
}
.t-state {
  text-align: center;
}
.t-state-loading,
.t-state-error {
  padding: 16px 0;
}
.t-state-success {
  text-align: left;
}
.t-spinner {
  width: 36px;
  height: 36px;
  margin: 0 auto 12px;
  border: 3px solid var(--border-strong);
  border-top-color: var(--brand);
  border-radius: 50%;
  animation: t-spin 0.8s linear infinite;
}
@keyframes t-spin {
  to { transform: rotate(360deg); }
}
.t-emoji {
  font-size: 34px;
  margin-bottom: 8px;
}
.t-state-title {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 600;
  color: var(--text);
}
.t-state-sub {
  margin: 0;
  font-size: 13px;
  color: var(--text-soft);
}
.t-state-hint {
  margin: 8px auto 0;
  max-width: 320px;
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.5;
}
.t-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 16px;
}
.t-source-tag {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  margin-bottom: 14px;
  background: var(--brand-soft);
  border: 1px solid var(--brand);
  border-radius: var(--radius-s);
  color: var(--brand-strong);
  font-size: 12px;
  font-weight: 600;
}
.t-source-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--brand);
  color: #fff;
  font-size: 10px;
  font-weight: 700;
}
.t-qr-section {
  text-align: center;
  margin-bottom: 14px;
}
.t-qr-title {
  font-size: 13px;
  color: var(--text);
  span {
    margin: 0 4px;
    color: var(--brand-strong);
    font-weight: 700;
  }
}
.t-qr-tip {
  margin: 4px 0 8px;
  font-size: 12px;
  color: var(--text-muted);
}
.t-qr-box {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 8px;
  background: #fff;
  border-radius: var(--radius-s);
  box-shadow: var(--shadow-s);
  min-height: 148px;
  min-width: 148px;
}
.t-open-btn {
  display: block;
  margin-bottom: 14px;
  padding: 13px 16px;
  border-radius: var(--radius-s);
  background: var(--brand);
  color: #fff;
  font-size: 15px;
  font-weight: 700;
  text-align: center;
  text-decoration: none;
  &:active {
    background: var(--brand-strong);
  }
}
.t-tips {
  margin-bottom: 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.t-tip {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 10px;
  border-radius: var(--radius-s);
  font-size: 12px;
  line-height: 1.5;
}
.t-tip-warn {
  background: #fff5f0;
  border: 1px solid var(--accent);
  color: #c2410c;
  strong { color: #ea580c; }
}
.t-tip-soft {
  background: var(--surface-2);
  color: var(--text-soft);
}
.t-tip-icon {
  flex-shrink: 0;
}
.t-link-block {
  margin-top: 4px;
}
.t-row-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 4px;
}
.t-mini-btn {
  border: none;
  background: transparent;
  color: var(--brand-strong);
  font-size: 12px;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: var(--radius-s);
  &:hover { background: var(--brand-soft); }
}
.t-link {
  display: block;
  padding: 10px 12px;
  background: var(--surface-2);
  border: 1px solid var(--border);
  border-radius: var(--radius-s);
  color: var(--brand-strong);
  font-size: 13px;
  word-break: break-all;
  text-decoration: none;
  line-height: 1.5;
  &:hover { border-color: var(--brand); }
}
.t-pwd {
  margin: 8px 0 0;
  font-size: 13px;
  color: var(--text-soft);
  strong {
    color: var(--text);
    letter-spacing: 1px;
  }
}
.t-btn {
  flex: 1;
  padding: 10px 12px;
  border: none;
  border-radius: var(--radius-s);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  text-align: center;
}
.t-btn-primary {
  background: var(--brand);
  color: #fff;
  &:hover { background: var(--brand-strong); }
}
.t-btn-ghost {
  background: var(--surface-2);
  color: var(--text-soft);
  border: 1px solid var(--border);
  &:hover {
    color: var(--brand-strong);
    border-color: var(--brand);
  }
}

.t-fade-enter-active,
.t-fade-leave-active {
  transition: opacity 0.2s ease;
}
.t-fade-enter-from,
.t-fade-leave-to {
  opacity: 0;
}
.t-zoom-enter-active {
  transition: transform 0.22s cubic-bezier(0.34, 1.56, 0.64, 1), opacity 0.22s ease;
}
.t-zoom-leave-active {
  transition: transform 0.18s ease, opacity 0.18s ease;
}
.t-zoom-enter-from {
  opacity: 0;
  transform: scale(0.92) translateY(8px);
}
.t-zoom-leave-to {
  opacity: 0;
  transform: scale(0.96);
}

@media (max-width: 480px) {
  .t-overlay {
    padding: 0;
    align-items: flex-end;
  }
  .t-box {
    max-width: 100%;
    max-height: 92vh;
    border-radius: var(--radius-l) var(--radius-l) 0 0;
  }
}
</style>
