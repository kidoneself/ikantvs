<script setup lang="ts">
/**
 * 首页：顶栏统一搜索；每日更新（后台录入）+ 热门推荐流。
 */
import { computed, onMounted, ref } from 'vue'
import { fetchFeed, fetchUpdatedFeed, type ResourceItem } from '@/api/content'
import { useInfiniteScroll } from '@/composables/useInfiniteScroll'
import ResourceCard from '@/components/ResourceCard.vue'

defineOptions({ name: 'Home' })

const PAGE_SIZE = 12

/** 后台每日更新（固定一小块，不无限滚） */
const daily = ref<ResourceItem[]>([])
const dailyLoading = ref(true)

type FeedTab = 'hot' | 'rating' | 'new'
const tab = ref<FeedTab>('hot')
const grid = ref<ResourceItem[]>([])
const page = ref(1)
const hasMore = ref(false)
const loading = ref(true)
const loadingMore = ref(false)
const error = ref(false)

const dailyIds = computed(() => new Set(daily.value.map((d) => d.id)))

function withoutDaily(items: ResourceItem[]) {
  if (!dailyIds.value.size) return items
  return items.filter((it) => !dailyIds.value.has(it.id))
}

async function loadDaily() {
  dailyLoading.value = true
  try {
    const res = await fetchUpdatedFeed({ page: 1, size: 24 })
    daily.value = res.items
  } catch {
    daily.value = []
  } finally {
    dailyLoading.value = false
  }
}

async function loadFirst() {
  loading.value = true
  error.value = false
  page.value = 1
  try {
    const res = await fetchFeed({ sort: tab.value, page: 1, size: PAGE_SIZE })
    grid.value = withoutDaily(res.items)
    hasMore.value = res.hasMore
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  page.value += 1
  try {
    const res = await fetchFeed({ sort: tab.value, page: page.value, size: PAGE_SIZE })
    grid.value.push(...withoutDaily(res.items))
    hasMore.value = res.hasMore
  } finally {
    loadingMore.value = false
  }
}

function changeTab(t: FeedTab) {
  if (tab.value === t) return
  tab.value = t
  void loadFirst()
}

const { sentinel } = useInfiniteScroll(loadMore)

onMounted(() => {
  void loadDaily().then(() => loadFirst())
})
</script>

<template>
  <div class="container home">
    <!-- 后台每日更新：有才展示，不拿别的凑 -->
    <section v-if="dailyLoading || daily.length" class="feed daily">
      <div class="section-head">
        <div class="title">每日更新</div>
      </div>
      <div v-if="dailyLoading" class="card-grid">
        <div v-for="n in 6" :key="n" class="card-skeleton" />
      </div>
      <div v-else class="card-grid">
        <ResourceCard
          v-for="(it, i) in daily"
          :key="it.id"
          :item="it"
          :rank="i + 1"
          source="home_daily"
        />
      </div>
    </section>

    <!-- 片库流：最热/高分/最新 -->
    <main class="feed">
      <div class="section-head">
        <div class="title">热门推荐</div>
        <div class="sorts">
          <span :class="{ on: tab === 'hot' }" @click="changeTab('hot')">最热</span>
          <span :class="{ on: tab === 'rating' }" @click="changeTab('rating')">高分</span>
          <span :class="{ on: tab === 'new' }" @click="changeTab('new')">最新</span>
        </div>
      </div>

      <div v-if="loading" class="card-grid">
        <div v-for="n in PAGE_SIZE" :key="n" class="card-skeleton" />
      </div>
      <div v-else-if="error" class="state">
        <p>加载失败，请检查网络后重试</p>
        <button type="button" @click="loadFirst">重新加载</button>
      </div>
      <div v-else-if="!grid.length" class="state">
        <p>暂无内容</p>
      </div>
      <template v-else>
        <div class="card-grid">
          <ResourceCard
            v-for="(it, i) in grid"
            :key="it.id"
            :item="it"
            :rank="i + 1"
            source="home_grid"
          />
        </div>
        <div v-if="hasMore" ref="sentinel" class="infinite-sentinel">
          <span v-if="loadingMore">加载中…</span>
        </div>
      </template>
    </main>
  </div>
</template>

<style scoped lang="scss">
.home {
  padding-bottom: 20px;

  :deep(.section-head) {
    margin: 0 0 12px;
  }
}

.feed {
  margin-top: 18px;
}

.daily {
  margin-top: 16px;
}

.sorts {
  display: flex;
  gap: 14px;
  font-size: 13px;
  span {
    cursor: pointer;
    color: var(--text-muted);
    &:hover {
      color: var(--text);
    }
    &.on {
      color: var(--brand);
      font-weight: 600;
    }
  }
}

@media (max-width: 640px) {
  .feed,
  .daily {
    margin-top: 14px;
  }
}
</style>
