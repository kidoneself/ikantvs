<script setup lang="ts">
/**
 * 老站式资源搜索：SSE 流式出链 + 网盘 Tab 本地筛选。
 * 触发：有关键词即开 SSE（不传 cloudTypes）；切 Tab 不重搜。
 */
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { streamSearch, type SearchLinkItem } from '@/api/search'
import { canTransfer, executeTransfer, fetchTransferResult } from '@/api/transfer'
import type { PanType } from '@/data/mock'
import { useSiteStore } from '@/store/site'
import TransferDialog from '@/components/TransferDialog.vue'
import { isThunderDownloadable, thunderDownload } from '@/lib/thunderLink'
import iconBaidu from '@/assets/icons/baidu.svg'
import iconQuark from '@/assets/icons/quark.svg'
import iconXunlei from '@/assets/icons/xunlei.svg'

const route = useRoute()
const router = useRouter()
const site = useSiteStore()

const PAN_META: Record<string, { label: string; icon?: string; letter: string }> = {
  magnet: { label: '磁力', letter: '磁' },
  baidu: { label: '百度网盘', icon: iconBaidu, letter: '度' },
  quark: { label: '夸克APP', icon: iconQuark, letter: '夸' },
  xunlei: { label: '迅雷云盘', icon: iconXunlei, letter: '迅' },
  uc: { label: 'UC网盘', letter: 'UC' },
  aliyun: { label: '阿里云盘', letter: '阿' },
  tianyi: { label: '天翼云盘', letter: '天' },
  mobile: { label: '移动云盘', letter: '移' },
  '115': { label: '115网盘', letter: '115' },
  '123': { label: '123网盘', letter: '123' },
  ed2k: { label: '电驴', letter: '驴' },
  pikpak: { label: 'PikPak', letter: 'P' },
}

function panMeta(panType: string) {
  return PAN_META[panType] ?? { label: panType, letter: panType.slice(0, 1).toUpperCase() }
}

const LABEL_TO_TYPE: Record<string, string> = {
  磁力: 'magnet',
  百度: 'baidu',
  夸克: 'quark',
  迅雷: 'xunlei',
  UC: 'uc',
  阿里: 'aliyun',
  天翼: 'tianyi',
  移动: 'mobile',
  '115': '115',
  '123': '123',
  其他: 'other',
}

const keyword = ref(String(route.query.q ?? ''))
const activePan = ref(String(route.query.pan ?? ''))
const allResults = ref<SearchLinkItem[]>([])
const searching = ref(false)
const hasSearched = ref(false)
const showBlockedDialog = ref(false)
let resultSeq = 0
let currentEs: EventSource | null = null
let lastSearchTime = 0
let syncingRoute = false

const enabledPanTypes = computed(() => {
  if (!site.enabledPans?.length) {
    return ['baidu', 'quark', 'xunlei', 'magnet']
  }
  const out: string[] = []
  for (const label of site.enabledPans) {
    const t = LABEL_TO_TYPE[label] ?? label.toLowerCase()
    if (t === 'other') {
      out.push('ed2k', 'pikpak')
    } else if (t) {
      out.push(t)
    }
  }
  return out.length ? out : ['baidu', 'quark', 'xunlei']
})

const counts = computed(() => {
  const m: Record<string, number> = {}
  for (const t of enabledPanTypes.value) m[t] = 0
  for (const item of allResults.value) {
    const p = item.panType
    if (p in m) m[p] += 1
    else if (enabledPanTypes.value.includes(p)) m[p] = (m[p] || 0) + 1
  }
  return m
})

const panTabs = computed(() =>
  enabledPanTypes.value.map((k) => {
    const meta = panMeta(k)
    return { key: k, label: meta.label, icon: meta.icon, letter: meta.letter, count: counts.value[k] ?? 0 }
  }),
)

const otherPanTabs = computed(() => panTabs.value.filter((t) => t.key !== activePan.value))

const filteredItems = computed(() => {
  const pan = activePan.value
  const MAX = 150
  return allResults.value
    .filter((i) => i.panType === pan)
    .filter((i) => !i.title || i.title.length <= MAX)
})

function pickDefaultPan(): string {
  const tabs = panTabs.value
  if (!tabs.length) return activePan.value || 'baidu'
  if (tabs.some((t) => t.key === activePan.value)) return activePan.value
  return (tabs.find((t) => t.count > 0) ?? tabs[0]).key
}

function syncRoute(q: string, pan: string) {
  syncingRoute = true
  router
    .replace({
      name: 'find',
      query: {
        ...(q ? { q } : {}),
        ...(pan ? { pan } : {}),
      },
    })
    .finally(() => {
      queueMicrotask(() => {
        syncingRoute = false
      })
    })
}

function closeStream() {
  if (currentEs) {
    currentEs.close()
    currentEs = null
  }
}

function toItem(raw: {
  title?: string
  url?: string | null
  cloudType?: string
  invalid?: boolean
  local?: boolean
  latestEpisode?: string | null
  mediaLinkId?: number | null
  mediaId?: number | null
}, source?: string): SearchLinkItem | null {
  const pan = (raw.cloudType || '').toLowerCase()
  if (!pan || !enabledPanTypes.value.includes(pan)) return null
  const meta = panMeta(pan)
  const magnetLike = pan === 'magnet' || pan === 'ed2k'
  const id = raw.mediaLinkId ?? undefined
  const encryptUrl = !magnetLike && !id && raw.url ? raw.url : null
  const plainUrl = magnetLike || raw.local ? raw.url : id ? raw.url : null
  const key = id != null ? `id:${id}` : `u:${raw.url || resultSeq}`
  return {
    key,
    id,
    title: raw.title || '未命名资源',
    panType: pan,
    panLabel: meta.label,
    source: source || undefined,
    mediaId: raw.mediaId ?? undefined,
    local: !!raw.local,
    url: plainUrl,
    encryptUrl,
    invalid: !!raw.invalid,
    latestEpisode: raw.latestEpisode,
    _seq: resultSeq++,
  }
}

function startStream() {
  const q = keyword.value.trim()
  if (!q) {
    closeStream()
    allResults.value = []
    resultSeq = 0
    hasSearched.value = false
    searching.value = false
    return
  }

  // 所有入口（顶栏跳转 / 本页再搜）共用：500ms 内不重复开 SSE
  const now = Date.now()
  if (now - lastSearchTime < 500) return
  lastSearchTime = now

  closeStream()
  allResults.value = []
  resultSeq = 0
  hasSearched.value = true
  searching.value = true
  if (!activePan.value || !enabledPanTypes.value.includes(activePan.value)) {
    activePan.value = enabledPanTypes.value[0] || 'baidu'
  }
  syncRoute(q, activePan.value)

  currentEs = streamSearch(
    { kw: q },
    (event) => {
      if (event.type === 'items' && event.items?.length) {
        appendItems(event.items, event.source, q)
      } else if (event.type === 'item' && event.item) {
        appendItems([event.item], event.source, q)
      } else if (event.type === 'complete') {
        searching.value = false
      } else if (event.type === 'error') {
        const msg = event.error || '搜索失败'
        if (msg.includes('违规')) showBlockedDialog.value = true
        else ElMessage.error(msg)
        searching.value = false
      }
    },
    () => {
      searching.value = false
    },
    () => {
      searching.value = false
      currentEs = null
    },
  )
}

/** 批量追加，减少 Vue 逐条响应开销 */
function appendItems(
  raws: Array<{
    title?: string
    url?: string | null
    cloudType?: string
    invalid?: boolean
    local?: boolean
    latestEpisode?: string | null
    mediaLinkId?: number | null
    mediaId?: number | null
  }>,
  source: string | undefined,
  q: string,
) {
  const seen = new Set(allResults.value.map((x) => x.key))
  const added: SearchLinkItem[] = []
  for (const raw of raws) {
    const item = toItem(raw, source)
    if (!item || seen.has(item.key)) continue
    seen.add(item.key)
    added.push(item)
  }
  if (!added.length) return
  allResults.value = allResults.value.concat(added)
  if (allResults.value.length === added.length || (counts.value[activePan.value] ?? 0) === 0) {
    const next = pickDefaultPan()
    if (next !== activePan.value) {
      activePan.value = next
      syncRoute(q, next)
    }
  }
}

function submit() {
  const q = keyword.value.trim()
  if (!q) return
  // 关键词变了走路由，由 watch 触发 SSE；同词再搜则直接重开流（防抖在 startStream）
  if (q === String(route.query.q ?? '').trim()) {
    startStream()
    return
  }
  syncRoute(q, activePan.value)
}

/** 切 Tab：纯本地，不发新请求（对齐老站）。 */
function changePan(pan: string) {
  if (activePan.value === pan) return
  activePan.value = pan
  syncRoute(keyword.value.trim(), pan)
}

watch(
  () => String(route.query.q ?? ''),
  (q) => {
    if (syncingRoute) {
      keyword.value = q
      return
    }
    const pan = String(route.query.pan ?? '')
    keyword.value = q
    if (pan) activePan.value = pan
    if (!q.trim()) {
      closeStream()
      hasSearched.value = false
      allResults.value = []
      searching.value = false
      return
    }
    startStream()
  },
  { immediate: true },
)

watch(
  () => String(route.query.pan ?? ''),
  (pan) => {
    if (syncingRoute || !pan) return
    // 仅同步 Tab，不重搜
    if (pan !== activePan.value) activePan.value = pan
  },
)

onBeforeUnmount(() => closeStream())

/* ------------------------------ 转存 / 磁力 ------------------------------ */

const dlg = reactive<{
  visible: boolean
  status: 'loading' | 'done' | 'failed'
  pan?: PanType
  title?: string
  shareUrl?: string
  password?: string
  message?: string
  keyword?: string
}>({ visible: false, status: 'loading' })

let lastItem: SearchLinkItem | null = null

function sleep(ms: number) {
  return new Promise((r) => setTimeout(r, ms))
}

/** 转存/迅雷 API 用短标签（百度/夸克/迅雷），勿用展示名「百度网盘」。 */
const PAN_TYPE_TO_LABEL: Record<string, PanType> = {
  magnet: '磁力',
  baidu: '百度',
  quark: '夸克',
  xunlei: '迅雷',
  uc: 'UC',
  aliyun: '阿里',
  tianyi: '天翼',
  mobile: '移动',
  '115': '115',
  '123': '123',
  ed2k: '其他',
  pikpak: '其他',
}

function panLabelOf(item: SearchLinkItem): PanType {
  return PAN_TYPE_TO_LABEL[item.panType] ?? ((item.panLabel as PanType) || '夸克')
}

function isMagnetLike(item: SearchLinkItem): boolean {
  const p = (item.panType || '').toLowerCase()
  return p === 'magnet' || p === 'ed2k'
}

function canGetLink(item: SearchLinkItem): boolean {
  if (item.invalid) return false
  if (isMagnetLike(item)) return !!item.url
  if (item.id) return canTransfer(panLabelOf(item))
  if (item.encryptUrl) return canTransfer(panLabelOf(item))
  return false
}

/** 链接本身死掉（分享删除等）；超时/限流不算，可重试。 */
function isLinkExpiredMessage(msg?: string | null): boolean {
  const e = msg || ''
  if (!e) return false
  if (e.includes('超时') || e.includes('网络') || e.includes('超限') || e.includes('频繁')) return false
  return (
    e.includes('已失效') ||
    e.includes('已删除') ||
    e.includes('已过期') ||
    e.includes('已取消') ||
    e.includes('分享已') ||
    e.includes('不存在') ||
    e.includes('没有访问权限')
  )
}

function markItemInvalid(item: SearchLinkItem) {
  item.invalid = true
  const idx = allResults.value.findIndex((x) => x.key === item.key)
  if (idx >= 0) allResults.value[idx] = { ...allResults.value[idx], invalid: true }
}

function actionLabel(item: SearchLinkItem): string {
  if (isMagnetLike(item)) {
    return site.thunderReady ? '迅雷下载' : '复制链接'
  }
  return '获取链接'
}

/** 追更识别集数（非 TMDB）：79 → 更新至第79集；7.15 → 更新至7.15 */
function episodeLabel(item: SearchLinkItem): string {
  const ep = (item.latestEpisode || '').trim()
  if (!ep) return ''
  if (/^\d+$/.test(ep)) return `更新至第${ep}集`
  return `更新至${ep}`
}

async function copyText(text: string) {
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
    ElMessage.success('已复制链接')
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

async function getLink(item: SearchLinkItem) {
  if (item.invalid || !canGetLink(item)) return
  if (isMagnetLike(item)) {
    if (!item.url) return
    if (site.thunderReady && isThunderDownloadable(item.panLabel as PanType, item.url)) {
      try {
        await thunderDownload({
          url: item.url,
          mediaTitle: item.mediaTitle || item.title,
          note: item.title,
          partnerCustom: site.xunleiPartnerCustom,
        })
        return
      } catch {
        /* fallback copy */
      }
    }
    await copyText(item.url)
    return
  }

  const pan = panLabelOf(item)
  if (!canTransfer(pan)) {
    ElMessage.warning('当前网盘暂不支持转存')
    return
  }
  lastItem = item
  dlg.visible = true
  dlg.status = 'loading'
  dlg.pan = pan
  dlg.title = item.title
  dlg.keyword = keyword.value.trim() || item.mediaTitle || item.title
  dlg.shareUrl = undefined
  dlg.password = undefined
  dlg.message = undefined
  try {
    const payload = item.id
      ? { mediaLinkId: item.id }
      : { encryptUrl: item.encryptUrl || undefined }
    let res = await executeTransfer(payload)
    let guard = 0
    while (res.status === 'transferring' && res.jobId && guard < 40) {
      await sleep(1500)
      res = await fetchTransferResult(res.jobId)
      guard += 1
    }
    if (res.status === 'done') {
      dlg.status = 'done'
      dlg.shareUrl = res.shareUrl
      dlg.password = res.password
    } else {
      const msg = res.message || (res.status === 'transferring' ? '转存超时，请重试' : '转存失败')
      dlg.status = 'failed'
      dlg.message = msg
      // 死链：本页划线，后端已写 invalid_share，下次搜索也不会再出
      if (isLinkExpiredMessage(msg)) markItemInvalid(item)
    }
  } catch (e) {
    const msg = e instanceof Error ? e.message : '转存失败，请稍后重试'
    dlg.status = 'failed'
    dlg.message = msg
    if (isLinkExpiredMessage(msg)) markItemInvalid(item)
  }
}

function retryTransfer() {
  if (lastItem && !lastItem.invalid) getLink(lastItem)
}
</script>

<template>
  <div class="find-page">
    <div class="search-box">
      <div class="search-inner">
        <input
          v-model="keyword"
          class="search-input"
          type="text"
          placeholder="只输入剧名即可，无需添加「第x季」"
          @keyup.enter="submit"
        />
        <button class="search-btn" type="button" :disabled="searching" @click="submit">
          {{ searching && !allResults.length ? '搜索中...' : '搜索' }}
        </button>
      </div>
    </div>

    <div v-if="showBlockedDialog" class="blocked-dialog-overlay" @click.self="showBlockedDialog = false">
      <div class="blocked-dialog">
        <h3>搜索内容不合规</h3>
        <p>该关键词包含违规内容，无法搜索</p>
        <button type="button" @click="showBlockedDialog = false">我知道了</button>
      </div>
    </div>

    <div v-if="!hasSearched" class="welcome">
      <div class="welcome-icon">
        <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" />
        </svg>
      </div>
      <h2>开始搜索资源</h2>
      <p>在上方搜索框输入关键词，搜索全网影视资源</p>
      <div class="welcome-tips">
        <div>支持电影、电视剧、动漫、综艺</div>
        <div>流式搜索，实时显示结果</div>
        <div>点「获取链接」一键转存</div>
      </div>
    </div>

    <div v-else class="list-box">
      <aside class="screen">
        <div class="screen-fixed">
          <h3>筛选</h3>
          <div class="pan-switch">
            <a
              v-for="t in panTabs"
              :key="t.key"
              href="javascript:;"
              :class="{ active: activePan === t.key }"
              @click="changePan(t.key)"
            >
              <span class="pan-label">
                <img v-if="t.icon" :src="t.icon" :alt="t.label" class="pan-icon" />
                <span v-else class="pan-letter">{{ t.letter }}</span>
                {{ t.label }}
              </span>
              <span v-if="t.count" class="tab-count">{{ t.count }}</span>
            </a>
          </div>
        </div>
      </aside>

      <main class="main">
        <div class="mobile-tabs">
          <button
            v-for="t in panTabs"
            :key="t.key"
            type="button"
            class="m-tab"
            :class="{ active: activePan === t.key }"
            @click="changePan(t.key)"
          >
            {{ t.label }}
            <span v-if="t.count" class="tab-count">{{ t.count }}</span>
          </button>
        </div>

        <div v-if="searching && !filteredItems.length" class="loading-box">
          <div class="loader" />
        </div>

        <div v-if="filteredItems.length" class="guide">
          <p>
            点击
            <span>{{ activePan === 'magnet' || activePan === 'ed2k' ? '复制/下载' : '获取链接' }}</span>
            查看分享地址 ↓↓
            <template v-if="searching">（继续检索中…）</template>
          </p>
        </div>

        <div v-if="filteredItems.length" class="list">
          <div v-for="item in filteredItems" :key="item.key" class="item" :class="{ dead: item.invalid }">
            <a
              href="javascript:;"
              class="title"
              :class="{ muted: !canGetLink(item), invalid: item.invalid }"
              @click="canGetLink(item) && getLink(item)"
            >{{ item.title }}{{ item.invalid ? '（已失效）' : '' }}</a>
            <div class="item-info">
              <div v-if="item.local" class="local-badge">
                <span class="local-btn">
                  站长精选<span v-if="episodeLabel(item)"> · {{ episodeLabel(item) }}</span>
                </span>
                <span class="local-desc">站长手动维护 · 失效进群联系补链</span>
              </div>
              <div v-else class="source">
                <img
                  v-if="panMeta(item.panType).icon"
                  :src="panMeta(item.panType).icon"
                  alt=""
                  class="source-icon"
                />
                <span v-else class="source-letter">{{ panMeta(item.panType).letter }}</span>
                <span>来源：{{ item.panLabel }}</span>
              </div>
              <button
                v-if="canGetLink(item)"
                class="get-btn"
                type="button"
                @click="getLink(item)"
              >
                {{ actionLabel(item) }}
              </button>
              <span v-else-if="item.invalid" class="get-na dead-tag">已失效</span>
              <span v-else class="get-na">暂不支持</span>
            </div>
          </div>
        </div>

        <div v-if="hasSearched && !searching && !filteredItems.length" class="empty">
          <p class="empty-desc">
            【<span class="empty-kw">{{ keyword }}</span>】
            {{ allResults.length ? '当前网盘没有结果' : '没有找到相关资源' }}
          </p>
          <div class="empty-tips">
            <span>试试只输入剧名，去掉「第x季」</span>
            <span>换个名称搜索（别名/英文名）</span>
          </div>
          <div v-if="allResults.length" class="empty-pans">
            <button
              v-for="t in otherPanTabs"
              :key="t.key"
              type="button"
              class="empty-pan-btn"
              :class="{ 'has-results': t.count > 0 }"
              @click="changePan(t.key)"
            >
              <img v-if="t.icon" :src="t.icon" alt="" class="pan-icon" />
              {{ t.label }}
              <span v-if="t.count" class="empty-pan-count">{{ t.count }}条</span>
            </button>
          </div>
        </div>
      </main>
    </div>

    <TransferDialog
      :visible="dlg.visible"
      :status="dlg.status"
      :pan="dlg.pan"
      :title="dlg.title"
      :share-url="dlg.shareUrl"
      :password="dlg.password"
      :message="dlg.message"
      :keyword="dlg.keyword"
      @close="dlg.visible = false"
      @retry="retryTransfer"
      @search="dlg.visible = false"
    />
  </div>
</template>


<style scoped lang="scss">
.find-page {
  padding-bottom: 60px;
}

.search-box {
  max-width: 1400px;
  margin: 0 auto;
  padding: 16px 20px;
}
.search-inner {
  display: flex;
  gap: 0;
  max-width: 720px;
  margin: 0 auto;
  border: 1px solid var(--border-strong);
  border-radius: 10px;
  overflow: hidden;
  background: var(--surface);
  box-shadow: var(--shadow-s);
  &:focus-within {
    border-color: var(--brand);
  }
}
.search-input {
  flex: 1;
  height: 48px;
  padding: 0 16px;
  border: none;
  background: transparent;
  color: var(--text);
  font-size: 15px;
  outline: none;
}
.search-btn {
  height: 48px;
  padding: 0 28px;
  border: none;
  background: var(--brand);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  &:hover { background: var(--brand-strong); }
  &:disabled { opacity: 0.7; cursor: default; }
}

.welcome {
  max-width: 560px;
  margin: 60px auto;
  padding: 0 20px;
  text-align: center;
  color: var(--text-soft);
  h2 {
    margin: 16px 0 8px;
    font-size: 22px;
    color: var(--text);
  }
  p { margin: 0 0 20px; font-size: 14px; }
}
.welcome-icon {
  color: var(--brand);
  opacity: 0.7;
}
.welcome-tips {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 13px;
  color: var(--text-muted);
}

.list-box {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 20px;
  display: flex;
  gap: 20px;
  align-items: flex-start;
}

.screen {
  width: 200px;
  flex-shrink: 0;
}
.screen-fixed {
  background: var(--surface);
  border-radius: 8px;
  padding: 16px;
  box-shadow: var(--shadow-s);
  position: sticky;
  top: 80px;
  h3 {
    margin: 0 0 16px;
    font-size: 16px;
    font-weight: 600;
    color: var(--text);
    padding-bottom: 12px;
    border-bottom: 1px solid var(--border);
  }
}
.pan-switch {
  display: flex;
  flex-direction: column;
  gap: 10px;
  a {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 14px;
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-radius: 6px;
    color: var(--text);
    text-decoration: none;
    font-size: 14px;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.2s;
    &:hover {
      background: var(--brand-soft);
      border-color: var(--brand);
    }
    &.active {
      background: var(--brand);
      color: #fff;
      border-color: var(--brand);
      font-weight: 600;
    }
  }
}
.pan-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.pan-icon {
  width: 18px;
  height: 18px;
  object-fit: contain;
}
.pan-letter,
.source-letter {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 4px;
  background: var(--brand-soft);
  color: var(--brand-strong);
  font-size: 10px;
  font-weight: 700;
}
.tab-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 16px;
  padding: 0 5px;
  border-radius: 8px;
  font-size: 11px;
  font-weight: 600;
  background: #e53935;
  color: #fff;
  margin-left: 4px;
}

.main {
  flex: 1;
  min-width: 0;
}

.mobile-tabs {
  display: none;
  gap: 8px;
  padding: 8px;
  margin-bottom: 16px;
  background: var(--surface-2);
  border-radius: 12px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: none; /* Firefox */
  -ms-overflow-style: none; /* IE/旧 Edge */
  &::-webkit-scrollbar {
    display: none; /* Chrome/Safari */
  }
}
.m-tab {
  flex: 1;
  min-width: max-content;
  height: 44px;
  padding: 0 12px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #f5f5f5;
  color: var(--text);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  &.active {
    background: var(--brand);
    color: #fff;
    border-color: var(--brand);
    font-weight: 700;
  }
}

.loading-box {
  text-align: center;
  padding: 30px 20px;
  background: var(--surface);
  border-radius: 12px;
  box-shadow: var(--shadow-s);
  margin-bottom: 16px;
}
.loader {
  margin: auto;
  width: fit-content;
  font-weight: bold;
  font-family: monospace;
  font-size: 20px;
  background: radial-gradient(circle closest-side, var(--brand) 94%, #0000) right / calc(200% - 1em) 100%;
  animation: scanText 1s infinite alternate linear;
  &::before {
    content: ' 全网检索中，请稍等...';
    line-height: 1em;
    color: #0000;
    background: inherit;
    background-image: radial-gradient(circle closest-side, #fff 94%, var(--brand));
    -webkit-background-clip: text;
    background-clip: text;
  }
}
@keyframes scanText {
  100% { background-position: left; }
}

.guide {
  margin-bottom: 12px;
  background: var(--surface);
  border-radius: 8px;
  padding: 14px;
  text-align: center;
  box-shadow: var(--shadow-s);
  p {
    margin: 0;
    font-size: 14px;
    color: var(--text-muted);
  }
  span {
    color: var(--brand);
    font-weight: 600;
  }
}

.list {
  background: var(--surface);
  border-radius: 8px;
  overflow: hidden;
  box-shadow: var(--shadow-s);
}
.item {
  padding: 18px;
  border-bottom: 1px solid #f5f5f5;
  transition: background 0.2s;
  &:hover { background: #fafafa; }
  &:last-child { border-bottom: none; }
}
.title {
  display: inline-block;
  font-size: 16px;
  font-weight: 600;
  color: var(--brand);
  margin: 0 0 10px;
  line-height: 1.5;
  text-decoration: none;
  cursor: pointer;
  word-break: break-word;
  &:hover {
    opacity: 0.75;
    text-decoration: underline;
  }
  &.muted {
    color: var(--text-soft);
    cursor: default;
    &:hover { opacity: 1; text-decoration: none; }
  }
  &.invalid {
    color: var(--text-muted);
    text-decoration: line-through;
    cursor: default;
    font-weight: 500;
    &:hover { opacity: 1; text-decoration: line-through; }
  }
}
.item.dead {
  opacity: 0.72;
}
.dead-tag {
  color: #e53935 !important;
}
.item-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 4px;
  gap: 16px;
}
.source {
  flex: 1;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #666;
}
.source-icon {
  width: 18px;
  height: 18px;
  object-fit: contain;
}
.local-badge {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.local-btn {
  display: inline-flex;
  align-items: center;
  padding: 5px 14px;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  background: #f97316;
  border-radius: 999px;
}
.local-desc {
  font-size: 13px;
  color: #f97316;
  font-weight: 500;
}
.get-btn {
  padding: 10px 24px;
  background: var(--brand);
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
  &:hover { opacity: 0.9; }
}
.get-na {
  flex-shrink: 0;
  font-size: 13px;
  color: var(--text-muted);
}

.empty {
  background: var(--surface);
  border-radius: 12px;
  padding: 32px 20px;
  text-align: center;
  box-shadow: var(--shadow-s);
}
.discover {
  text-align: left;
}
.discover-head {
  margin-bottom: 16px;
  h2 {
    margin: 0;
    font-size: 18px;
    font-weight: 800;
    color: var(--text);
  }
  p {
    margin: 6px 0 0;
    font-size: 13px;
    color: var(--text-muted);
  }
}
.discover-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 14px;
}
.discover-loading {
  padding: 24px 0;
  font-size: 14px;
  color: var(--text-muted);
}
.empty-desc {
  margin: 0 0 12px;
  font-size: 15px;
  color: var(--text-soft);
}
.empty-kw {
  color: var(--brand);
  font-weight: 600;
}
.empty-tips {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 20px;
  font-size: 13px;
  color: var(--text-muted);
}
.empty-pans {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
}
.empty-pan-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 16px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--surface-2);
  color: var(--text);
  font-size: 13px;
  cursor: pointer;
  &.has-results {
    border-color: var(--brand);
    background: var(--brand-soft);
    color: var(--brand-strong);
  }
}
.empty-pan-count {
  font-size: 11px;
  font-weight: 600;
  color: #e53935;
}

.more {
  text-align: center;
  margin-top: 16px;
}
.more-btn {
  padding: 10px 24px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--surface);
  color: var(--text-soft);
  font-size: 14px;
  cursor: pointer;
  &:hover {
    border-color: var(--brand);
    color: var(--brand-strong);
  }
  &:disabled { opacity: 0.6; cursor: default; }
}

@media (max-width: 900px) {
  .screen { display: none; }
  .mobile-tabs { display: flex; }
  .list-box { padding: 0 12px; }
  .item { padding: 14px; }
  .title { font-size: 15px; }
  .get-btn { padding: 8px 16px; font-size: 13px; }
}

.blocked-dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,.45);
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
}
.blocked-dialog {
  background: var(--surface);
  border-radius: 12px;
  padding: 24px;
  max-width: 360px;
  text-align: center;
  h3 { margin: 0 0 8px; }
  p { margin: 0 0 16px; color: var(--text-muted); font-size: 14px; }
  button {
    padding: 8px 20px;
    border: none;
    border-radius: 8px;
    background: var(--brand);
    color: #fff;
    cursor: pointer;
  }
}
</style>
