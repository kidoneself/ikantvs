<script setup lang="ts">
/**
 * 短剧专区：独立库列表/搜索，点夸克/百度走现有转存弹窗。
 * 顶栏全局搜仍进资源搜索；本页搜索框只搜短剧。
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { fetchDramaCount, fetchDramaList, searchDrama, type DramaItem } from '@/api/drama'
import { canTransfer, executeTransfer, fetchTransferResult } from '@/api/transfer'
import CoverImage from '@/components/CoverImage.vue'
import TransferDialog from '@/components/TransferDialog.vue'
import { useInfiniteScroll } from '@/composables/useInfiniteScroll'
import { findQuery } from '@/lib/findSearch'
import type { PanType } from '@/data/mock'

defineOptions({ name: 'Drama' })

const route = useRoute()
const router = useRouter()

const PAGE_SIZE = 30
const kw = ref('')
const activeQ = ref('')
const page = ref(1)
const total = ref(0)
const totalCount = ref(0)
const rows = ref<DramaItem[]>([])
const loading = ref(false)
const loadError = ref(false)

const hasMore = computed(() => rows.value.length < total.value)
const subtitle = computed(() => {
  if (activeQ.value) return `「${activeQ.value}」共 ${total.value} 部`
  const n = totalCount.value || total.value
  return n > 0 ? `共 ${n.toLocaleString()} 部短剧` : '海量短剧，点一下即可转存'
})

/**
 * 短剧专区不受域名网盘开关影响（开关只约束搜索列表出迅雷/磁力等）。
 * 有夸克/百度链就展示并可转存。
 */
const visibleRows = computed(() => rows.value)

async function load(reset: boolean) {
  if (loading.value) return
  if (reset) {
    page.value = 1
    rows.value = []
  }
  loading.value = true
  loadError.value = false
  try {
    const resp = activeQ.value
      ? await searchDrama(activeQ.value, page.value, PAGE_SIZE)
      : await fetchDramaList(page.value, PAGE_SIZE)
    total.value = Number(resp?.total || 0)
    const list = Array.isArray(resp?.records) ? resp.records : []
    rows.value = reset ? list : rows.value.concat(list)
  } catch {
    if (reset) loadError.value = true
  } finally {
    loading.value = false
  }
}

function submitSearch() {
  const q = kw.value.trim()
  if (q === activeQ.value) return
  activeQ.value = q
  router.replace({ name: 'drama', query: q ? { q } : {} })
  void load(true)
}

function clearSearch() {
  kw.value = ''
  if (!activeQ.value) return
  activeQ.value = ''
  router.replace({ name: 'drama' })
  void load(true)
}

function loadMore() {
  if (loading.value || !hasMore.value) return
  page.value += 1
  void load(false)
}

const { sentinel } = useInfiniteScroll(loadMore)

function goFind() {
  if (activeQ.value) router.push(findQuery(activeQ.value))
}

/* ------------------------------ 转存 ------------------------------ */

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

let last: { item: DramaItem; pan: '夸克' | '百度' } | null = null

function sleep(ms: number) {
  return new Promise((r) => setTimeout(r, ms))
}

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

function markDead(item: DramaItem, pan: '夸克' | '百度') {
  if (pan === '夸克') item.quarkLink = undefined
  else item.baiduLink = undefined
}

async function transfer(item: DramaItem, pan: '夸克' | '百度') {
  const token = pan === '夸克' ? item.quarkLink : item.baiduLink
  // 短剧专区不受域名 pans 开关约束（那只影响搜索列表）
  if (!token || !canTransfer(pan)) return

  last = { item, pan }
  dlg.visible = true
  dlg.status = 'loading'
  dlg.pan = pan
  dlg.title = item.title
  dlg.keyword = item.title
  dlg.shareUrl = undefined
  dlg.password = undefined
  dlg.message = undefined

  try {
    let res = await executeTransfer({ encryptUrl: token })
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
      if (isLinkExpiredMessage(msg)) markDead(item, pan)
    }
  } catch (e) {
    const msg = e instanceof Error ? e.message : '转存失败，请稍后重试'
    dlg.status = 'failed'
    dlg.message = msg
    if (isLinkExpiredMessage(msg)) markDead(item, pan)
  }
}

function onPosterClick(item: DramaItem) {
  if (item.quarkLink) {
    void transfer(item, '夸克')
  } else if (item.baiduLink) {
    void transfer(item, '百度')
  } else {
    ElMessage.info('暂无可转存的网盘链接')
  }
}

function retryTransfer() {
  if (last) void transfer(last.item, last.pan)
}

onMounted(async () => {
  const q = typeof route.query.q === 'string' ? route.query.q.trim() : ''
  kw.value = q
  activeQ.value = q
  try {
    totalCount.value = await fetchDramaCount()
  } catch {
    /* ignore */
  }
  void load(true)
})

watch(
  () => String(route.query.q ?? ''),
  (q) => {
    const next = q.trim()
    if (next === activeQ.value) return
    kw.value = next
    activeQ.value = next
    void load(true)
  },
)
</script>

<template>
  <div class="container drama-page">
    <div class="section-head">
      <div class="title">短剧</div>
      <p class="count">{{ subtitle }}</p>
    </div>

    <form class="search" @submit.prevent="submitSearch">
      <input
        v-model="kw"
        class="search-input"
        type="search"
        placeholder="搜短剧名…"
        enterkeyhint="search"
        autocomplete="off"
      />
      <button v-if="kw" class="search-clear" type="button" aria-label="清空" @click="clearSearch">
        ×
      </button>
      <button class="search-btn" type="submit">搜索</button>
    </form>

    <div v-if="loadError" class="state">
      <p>加载失败</p>
      <button type="button" @click="load(true)">重试</button>
    </div>

    <div v-else-if="loading && !rows.length" class="card-grid">
      <div v-for="n in 12" :key="n" class="card-skeleton" />
    </div>

    <div v-else-if="!visibleRows.length" class="state">
      <p>{{ activeQ ? '没有找到相关短剧' : '暂时没有短剧' }}</p>
      <button v-if="activeQ" type="button" @click="goFind">去资源搜索「{{ activeQ }}」</button>
    </div>

    <template v-else>
      <div class="card-grid">
        <article v-for="item in visibleRows" :key="item.id" class="card">
          <button class="poster" type="button" :aria-label="item.title" @click="onPosterClick(item)">
            <CoverImage :title="item.title" :thumb="item.coverImage" category="剧集" />
            <span class="cat">短剧</span>
            <span v-if="item.episodeCount" class="ep">{{ item.episodeCount }}集</span>
          </button>
          <div class="meta">
            <h3 class="name">{{ item.title }}</h3>
          </div>
          <div class="foot">
            <button
              v-if="item.quarkLink"
              class="pan quark"
              type="button"
              @click="transfer(item, '夸克')"
            >
              夸克
            </button>
            <button
              v-if="item.baiduLink"
              class="pan baidu"
              type="button"
              @click="transfer(item, '百度')"
            >
              百度
            </button>
          </div>
        </article>
      </div>
    </template>

    <div v-if="hasMore" ref="sentinel" class="infinite-sentinel">
      <span v-if="loading">加载中…</span>
    </div>
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
    @search="
      () => {
        dlg.visible = false
        if (dlg.keyword) router.push(findQuery(dlg.keyword))
      }
    "
  />
</template>

<style scoped lang="scss">
.drama-page {
  /* 只改上下，勿写 padding 简写，否则会清掉 .container 的左右边距 */
  padding-top: 16px;
  padding-bottom: 72px;

  :deep(.section-head) {
    margin: 0 0 12px;
    justify-content: flex-start;
    gap: 10px;
    flex-wrap: wrap;
  }
}

.count {
  margin: 0;
  font-size: 13px;
  color: var(--text-muted);
}

.search {
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: 420px;
  margin: 0 0 16px;
  min-width: 0;
}

.search-input {
  flex: 1;
  min-width: 0;
  height: 38px;
  padding: 0 12px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--surface);
  font-size: 14px;
  outline: none;
  -webkit-appearance: none;

  &:focus {
    border-color: var(--brand);
    box-shadow: 0 0 0 3px rgba(22, 184, 125, 0.14);
  }
}

.search-btn {
  flex-shrink: 0;
  height: 38px;
  padding: 0 14px;
  border: none;
  border-radius: 10px;
  background: var(--brand);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}

.search-clear {
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: var(--surface-2);
  color: var(--text-soft);
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
}

/* 海报比例对齐 ResourceCard；底部保留可点网盘按钮 */
.card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--surface);
  border-radius: var(--radius);
  overflow: hidden;
  box-shadow: var(--shadow-s);
  border: 1px solid var(--border);
  transition: transform 0.18s, box-shadow 0.18s;

  &:hover {
    transform: translateY(-4px);
    box-shadow: var(--shadow-hover);

    .poster :deep(img) {
      transform: scale(1.06);
    }
  }
}

.poster {
  position: relative;
  display: block;
  width: 100%;
  aspect-ratio: 5 / 6;
  padding: 0;
  border: none;
  cursor: pointer;
  overflow: hidden;
  background: var(--surface-2);

  :deep(.cover),
  :deep(img),
  :deep(.placeholder) {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  :deep(img) {
    transition: transform 0.4s ease;
  }
}

.cat {
  position: absolute;
  top: 8px;
  left: 8px;
  background: rgba(22, 184, 125, 0.92);
  color: #fff;
  font-size: 11px;
  padding: 2px 7px;
  border-radius: 6px;
}

.ep {
  position: absolute;
  bottom: 8px;
  right: 8px;
  background: rgba(0, 0, 0, 0.62);
  color: #fff;
  font-weight: 700;
  font-size: 12px;
  padding: 2px 7px;
  border-radius: 6px;
  backdrop-filter: blur(4px);
}

.meta {
  padding: 10px 12px 8px;
}

.name {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.foot {
  display: flex;
  margin-top: auto;
  border-top: 1px solid var(--border);
}

.pan {
  flex: 1;
  height: 36px;
  border: none;
  background: transparent;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;

  & + .pan {
    border-left: 1px solid var(--border);
  }
  &.quark {
    color: var(--pan-quark);
  }
  &.baidu {
    color: var(--pan-baidu);
  }
  &:active {
    background: var(--surface-2);
  }
}

@media (max-width: 640px) {
  .drama-page {
    padding-top: 12px;
    padding-bottom: 80px;
  }

  .search {
    max-width: none;
    width: 100%;
    margin-bottom: 12px;
    gap: 6px;
    padding: 3px;
    border-radius: 12px;
    background: var(--surface);
    border: 1px solid var(--border);
    box-shadow: var(--shadow-s);
  }

  .search-input {
    height: 34px;
    border: none;
    box-shadow: none;
    background: transparent;
    padding: 0 10px;
    font-size: 14px;

    &:focus {
      box-shadow: none;
    }
  }

  .search-btn {
    height: 32px;
    padding: 0 12px;
    border-radius: 9px;
    font-size: 13px;
  }

  .search-clear {
    width: 28px;
    height: 28px;
  }

  .meta {
    padding: 8px 10px 6px;
  }

  .name {
    font-size: 13px;
  }

  .cat {
    top: 6px;
    left: 6px;
    font-size: 10px;
    padding: 1px 6px;
  }

  .ep {
    bottom: 6px;
    right: 6px;
    font-size: 11px;
    padding: 1px 6px;
  }

  .pan {
    height: 34px;
    font-size: 12px;
  }

  .card:hover {
    transform: none;
  }
}
</style>
