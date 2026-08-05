<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  searchFeed,
  fetchDiscover,
  importDiscover,
  type ResourceItem,
  type SortKey,
  type TmdbDiscoverItem,
} from '@/api/content'
import { track } from '@/api/track'
import { findQuery } from '@/lib/findSearch'
import { useInfiniteScroll } from '@/composables/useInfiniteScroll'
import ResourceCard from '@/components/ResourceCard.vue'
import DiscoverCard from '@/components/DiscoverCard.vue'

defineOptions({ name: 'Search' })

const PAGE_SIZE = 12

const route = useRoute()
const router = useRouter()

const kw = ref((route.query.q as string) || '')
const cat = ref((route.query.cat as string) || '全部')

// —— 筛选项（类型由顶部导航栏统一控制，这里不重复）——
const SORT_OPTIONS: { label: string; v: SortKey }[] = [
  { label: '热度降序', v: 'hot' },
  { label: '热度升序', v: 'hot_asc' },
  { label: '上映降序', v: 'new' },
  { label: '上映升序', v: 'release_asc' },
  { label: '评分降序', v: 'rating' },
  { label: '评分升序', v: 'rating_asc' },
]

const NOW_YEAR = new Date().getFullYear()
const YEAR_OPTIONS: { label: string; from?: number; to?: number }[] = [
  { label: '全部' },
  ...Array.from({ length: 6 }, (_, i) => {
    const y = NOW_YEAR - i
    return { label: String(y), from: y, to: y }
  }),
  { label: '2010-2019', from: 2010, to: 2019 },
  { label: '2000-2009', from: 2000, to: 2009 },
  { label: '更早', to: 1999 },
]

const GENRE_OPTIONS = [
  '全部', '剧情', '喜剧', '动作', '爱情', '科幻', '悬疑', '惊悚',
  '犯罪', '恐怖', '奇幻', '冒险', '动画', '战争', '武侠', '家庭',
  '纪录', '音乐', '历史', '西部',
]

// label → ISO 码（country 字段存 ISO 码）
const REGION_OPTIONS: { label: string; code?: string }[] = [
  { label: '全部' },
  { label: '中国大陆', code: 'CN' },
  { label: '美国', code: 'US' },
  { label: '日本', code: 'JP' },
  { label: '韩国', code: 'KR' },
  { label: '中国香港', code: 'HK' },
  { label: '中国台湾', code: 'TW' },
  { label: '英国', code: 'GB' },
  { label: '法国', code: 'FR' },
  { label: '德国', code: 'DE' },
  { label: '泰国', code: 'TH' },
  { label: '印度', code: 'IN' },
  { label: '意大利', code: 'IT' },
  { label: '西班牙', code: 'ES' },
  { label: '加拿大', code: 'CA' },
]

const RATING_OPTIONS: { label: string; v: number }[] = [
  { label: '不限', v: 0 },
  { label: '9分+', v: 9 },
  { label: '8分+', v: 8 },
  { label: '7分+', v: 7 },
  { label: '6分+', v: 6 },
  { label: '5分+', v: 5 },
]

const sort = ref<SortKey>('hot')
const yearIdx = ref(0)
const genre = ref('全部')
const regionIdx = ref(0)
const minRating = ref(0)

const results = ref<ResourceItem[]>([])
const total = ref(0)
const page = ref(1)
const hasMore = ref(false)
const loading = ref(true)
const loadingMore = ref(false)
const error = ref(false)

const discoverItems = ref<TmdbDiscoverItem[]>([])
const discoverLoading = ref(false)
const importingKey = ref<string | null>(null)

function shouldFetchDiscover() {
  return kw.value.trim().length >= 2
}

// 站内结果已展示的 id，用来给下方 TMDB 补充去重。
const shownIds = computed(() => new Set(results.value.map((r) => r.id)))

// 站内有结果时，补充 TMDB 里「上方没出现过」的相关条目：
// 未入库的（点击添加）+ 在库但因别名/译名不同没被站内搜到的（点击进资源搜索）。
const discoverExtra = computed(() =>
  discoverItems.value.filter((it) => !it.localId || !shownIds.value.has(it.localId)),
)

// 每次新搜索自增，用于丢弃「过期」的 TMDB 发现响应（发现是后台异步补充，
// 快速改关键词/筛选时旧请求可能后到，不能覆盖新结果）。
let discoverSeq = 0

async function loadDiscover() {
  const seq = ++discoverSeq
  discoverItems.value = []
  if (!shouldFetchDiscover()) return
  discoverLoading.value = true
  try {
    const items = await fetchDiscover(kw.value, cat.value !== '全部' ? cat.value : undefined)
    if (seq === discoverSeq) discoverItems.value = items
  } finally {
    if (seq === discoverSeq) discoverLoading.value = false
  }
}

async function onPickDiscover(item: TmdbDiscoverItem) {
  const title = item.title || item.originalTitle || ''
  if (item.localId) {
    router.push(findQuery(title))
    return
  }
  const key = `${item.tmdbId}-${item.type}`
  if (importingKey.value === key) return
  importingKey.value = key
  try {
    const m = await importDiscover(item.tmdbId, item.type)
    if (!m.id) {
      ElMessage.error('添加失败，请稍后重试')
      return
    }
    router.push(findQuery(m.title || title))
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '添加失败')
  } finally {
    importingKey.value = null
  }
}

function queryParams(p: number) {
  const y = YEAR_OPTIONS[yearIdx.value]
  return {
    q: kw.value,
    cat: cat.value,
    page: p,
    size: PAGE_SIZE,
    sort: sort.value,
    yearFrom: y.from,
    yearTo: y.to,
    genre: genre.value === '全部' ? undefined : genre.value,
    country: REGION_OPTIONS[regionIdx.value].code,
    minRating: minRating.value,
  }
}

async function loadFirst() {
  loading.value = true
  error.value = false
  page.value = 1
  try {
    const res = await searchFeed(queryParams(1))
    results.value = res.items
    total.value = res.total
    hasMore.value = res.hasMore
    if (kw.value.trim()) track('search', { keyword: kw.value.trim(), num: res.total })
  } catch {
    error.value = true
    return
  } finally {
    loading.value = false
  }
  // 站内结果先渲染完，TMDB 发现在后台补：0 命中时整块当发现结果，有命中时只补未入库的版本。
  // 关键：不要 await，否则慢/挂起的 TMDB 请求会一直卡在骨架屏上（表现为「空白，必须刷新」）。
  void loadDiscover()
}

async function loadMore() {
  if (loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  page.value += 1
  const res = await searchFeed(queryParams(page.value))
  results.value.push(...res.items)
  hasMore.value = res.hasMore
  loadingMore.value = false
}

const { sentinel } = useInfiniteScroll(loadMore)

function reset() {
  sort.value = 'hot'
  yearIdx.value = 0
  genre.value = '全部'
  regionIdx.value = 0
  minRating.value = 0
}

watch(
  () => route.query,
  () => {
    kw.value = (route.query.q as string) || ''
    cat.value = (route.query.cat as string) || '全部'
    reset()
    loadFirst()
  },
  { immediate: true },
)
</script>

<template>
  <div class="container search-page">
    <div class="page-head">
      <h1 class="h-title">
        <template v-if="kw">搜索「{{ kw }}」</template>
        <template v-else-if="cat !== '全部'">{{ cat }}</template>
        <template v-else>全部资源</template>
      </h1>
      <p class="sources">共 <em>{{ total }}</em> 条相关信息流</p>
    </div>

    <!-- 筛选面板 -->
    <div class="filters">
      <div class="filter-row">
        <span class="f-label">排序</span>
        <div class="chips">
          <button v-for="s in SORT_OPTIONS" :key="s.v" :class="{ on: sort === s.v }" @click="sort = s.v; loadFirst()">
            {{ s.label }}
          </button>
        </div>
      </div>
      <div class="filter-row">
        <span class="f-label">风格</span>
        <div class="chips">
          <button v-for="g in GENRE_OPTIONS" :key="g" :class="{ on: genre === g }" @click="genre = g; loadFirst()">
            {{ g }}
          </button>
        </div>
      </div>
      <div class="filter-row">
        <span class="f-label">地区</span>
        <div class="chips">
          <button v-for="(r, i) in REGION_OPTIONS" :key="r.label" :class="{ on: regionIdx === i }" @click="regionIdx = i; loadFirst()">
            {{ r.label }}
          </button>
        </div>
      </div>
      <div class="filter-row">
        <span class="f-label">年份</span>
        <div class="chips">
          <button v-for="(y, i) in YEAR_OPTIONS" :key="y.label" :class="{ on: yearIdx === i }" @click="yearIdx = i; loadFirst()">
            {{ y.label }}
          </button>
        </div>
      </div>
      <div class="filter-row">
        <span class="f-label">评分</span>
        <div class="chips">
          <button v-for="r in RATING_OPTIONS" :key="r.v" :class="{ on: minRating === r.v }" @click="minRating = r.v; loadFirst()">
            {{ r.label }}
          </button>
        </div>
      </div>
    </div>

    <!-- 结果网格 -->
    <div v-if="loading" class="card-grid">
      <div v-for="n in 12" :key="n" class="card-skeleton" />
    </div>

    <div v-else-if="error" class="state">
      <p>加载失败，请检查网络后重试</p>
      <button @click="loadFirst">重新加载</button>
    </div>

    <template v-else>
      <div v-if="results.length" class="card-grid">
        <ResourceCard v-for="(it, i) in results" :key="it.id" :item="it" :rank="i + 1" source="search" />
      </div>

      <template v-else>
        <section v-if="discoverItems.length" class="discover">
          <div class="discover-head">
            <h2>TMDB 发现</h2>
            <p>站内暂无「{{ kw }}」，以下为 TMDB 匹配结果（最多 8 条），点击卡片添加</p>
          </div>
          <div class="card-grid">
            <DiscoverCard
              v-for="item in discoverItems"
              :key="`${item.tmdbId}-${item.type}`"
              :item="item"
              :loading="importingKey === `${item.tmdbId}-${item.type}`"
              @pick="onPickDiscover(item)"
            />
          </div>
        </section>
        <div v-else-if="discoverLoading" class="state">
          <p>正在查找 TMDB…</p>
        </div>
        <el-empty v-else description="没有匹配的信息流，换个关键词或筛选条件试试" />
      </template>

      <div v-if="hasMore" ref="sentinel" class="infinite-sentinel">
        <span v-if="loadingMore">加载中…</span>
      </div>

      <!-- 站内有结果时，把 TMDB 里上方没出现过的相关条目补在底部 -->
      <section v-if="results.length && discoverExtra.length" class="discover more-discover">
        <div class="discover-head">
          <h2>更多相关</h2>
          <p>以下为 TMDB 匹配到的其它版本，点击卡片查看或添加</p>
        </div>
        <div class="card-grid">
          <DiscoverCard
            v-for="item in discoverExtra"
            :key="`${item.tmdbId}-${item.type}`"
            :item="item"
            :loading="importingKey === `${item.tmdbId}-${item.type}`"
            @pick="onPickDiscover(item)"
          />
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped lang="scss">
.search-page {
  padding-top: 22px;
}

.page-head {
  margin-bottom: 14px;
  .h-title {
    margin: 0;
    font-size: 24px;
    font-weight: 800;
  }
  .sources {
    margin: 6px 0 0;
    font-size: 13px;
    color: var(--text-muted);
    em {
      color: var(--brand);
      font-style: normal;
      font-weight: 700;
    }
  }
}

.filters {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px;
  margin-bottom: 18px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
}
.filter-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}
.f-label {
  flex: 0 0 auto;
  width: 36px;
  padding-top: 5px;
  font-size: 13px;
  color: var(--text-muted);
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
  button {
    height: 28px;
    padding: 0 12px;
    border: 0;
    border-radius: 999px;
    background: transparent;
    color: var(--text-soft);
    font-size: 13px;
    cursor: pointer;
    transition:
      background 0.15s,
      color 0.15s;
    &:hover {
      color: var(--text);
      background: var(--surface-2);
    }
    &.on {
      background: var(--brand-soft);
      color: var(--brand-strong);
      font-weight: 600;
    }
  }
}

.discover {
  margin-top: 4px;
}
.more-discover {
  margin-top: 28px;
  padding-top: 22px;
  border-top: 1px solid var(--border);
}
.discover-head {
  margin-bottom: 14px;
  h2 {
    margin: 0;
    font-size: 18px;
    font-weight: 800;
  }
  p {
    margin: 6px 0 0;
    font-size: 13px;
    color: var(--text-muted);
  }
}

.state {
  padding: 48px 0;
  text-align: center;
  color: var(--text-muted);
  button {
    margin-top: 12px;
  }
}
@media (max-width: 600px) {
  /* 手机端：每行筛选改成横向滑动，避免换行把面板撑太高 */
  .filters {
    padding: 10px 0;
    gap: 2px;
    border-radius: 12px;
  }
  .filter-row {
    align-items: center;
    gap: 6px;
  }
  .f-label {
    width: auto;
    padding: 0 0 0 12px;
    font-size: 12px;
  }
  .chips {
    flex-wrap: nowrap;
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
    scrollbar-width: none;
    padding: 4px 12px 4px 2px;
    &::-webkit-scrollbar {
      display: none;
    }
    button {
      flex: 0 0 auto;
    }
  }
}
</style>
