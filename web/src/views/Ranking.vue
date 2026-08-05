<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { fetchRanking, fetchRankingBoards, type RankKey, type ResourceItem } from '@/api/content'
import { track } from '@/api/track'
import CoverImage from '@/components/CoverImage.vue'
import { findQuery } from '@/lib/findSearch'

defineOptions({ name: 'Ranking' })

const router = useRouter()

function go(it: ResourceItem, rank: number) {
  track('card_click', { mediaId: it.id, tag: 'ranking', num: rank })
  router.push(findQuery(it.title))
}

interface Tab {
  key: string
  label: string
  desc: string
}

// 自动榜（无策划榜单时的回退方案）：最热 + 最新（最新=按上映时间）
const AUTO_TABS: Tab[] = [
  { key: 'hot', label: '最热', desc: '热度最高的作品' },
  { key: 'new', label: '最新', desc: '按上映/开播时间最新的作品' },
]

const curated = ref(false)
const tabs = ref<Tab[]>(AUTO_TABS)
const boards = ref<Record<string, ResourceItem[]>>({})
const active = ref<string>('hot')
const activeTab = computed(() => tabs.value.find((t) => t.key === active.value) || tabs.value[0])

const list = ref<ResourceItem[]>([])
const loading = ref(true)

async function loadActive(key: string, prefetched?: ResourceItem[]) {
  loading.value = true
  try {
    if (curated.value) {
      list.value = boards.value[key] || []
    } else if (prefetched) {
      list.value = prefetched
    } else {
      list.value = await fetchRanking(key as RankKey)
    }
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  loading.value = true
  try {
    // 先拉策划榜；有货就展示。不要和 /media 绑死在 Promise.all——一侧失败会整页卡在 skeleton。
    const bs = await fetchRankingBoards()
    if (bs.length) {
      curated.value = true
      tabs.value = bs.map((b) => ({ key: b.slug, label: b.name, desc: b.description || '' }))
      boards.value = Object.fromEntries(bs.map((b) => [b.slug, b.items]))
      active.value = tabs.value[0].key
      list.value = boards.value[active.value] || []
      return
    }
    curated.value = false
    tabs.value = AUTO_TABS
    await loadActive(active.value)
  } catch {
    curated.value = false
    tabs.value = AUTO_TABS
    list.value = []
  } finally {
    loading.value = false
  }
})

watch(active, (key) => {
  loadActive(key)
})
</script>

<template>
  <div class="container ranking">
    <div class="page-head">
      <h1>榜单</h1>
      <p class="sub">{{ activeTab.desc }}</p>
    </div>

    <div class="tabs">
      <span
        v-for="t in tabs"
        :key="t.key"
        class="tab"
        :class="{ on: active === t.key }"
        @click="active = t.key"
        >{{ t.label }}</span
      >
    </div>

    <div v-if="loading" class="rank-list">
      <div v-for="n in 8" :key="n" class="rank-skeleton" />
    </div>

    <ol v-else class="rank-list">
      <li
        v-for="(it, i) in list"
        :key="it.id"
        class="rank-row"
        @click="go(it, i + 1)"
      >
        <span class="no" :class="{ top: i < 3 }">{{ i + 1 }}</span>
        <div class="thumb">
          <CoverImage
            :title="it.title"
            :poster="it.poster"
            :thumb="it.posterThumb"
            :category="it.category"
          />
        </div>
        <div class="main">
          <h3 class="title">{{ it.title }}</h3>
          <p class="meta">
            <span class="rating" v-if="it.rating">★ {{ it.rating.toFixed(1) }}</span>
            {{ it.category }}<template v-if="it.year"> · {{ it.year }}</template><template v-if="it.region"> · {{ it.region }}</template>
          </p>
          <div class="tags">
            <span v-for="g in (it.genres || []).slice(0, 3)" :key="g" class="tag">{{ g }}</span>
          </div>
        </div>
      </li>
    </ol>
  </div>
</template>

<style scoped lang="scss">
.ranking {
  padding-top: 22px;
}
.page-head {
  margin-bottom: 16px;
  h1 {
    margin: 0;
    font-size: 26px;
    font-weight: 800;
  }
  .sub {
    margin: 6px 0 0;
    font-size: 14px;
    color: var(--text-muted);
  }
}

.tabs {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 18px;
  .tab {
    padding: 8px 18px;
    border-radius: 999px;
    font-size: 14px;
    font-weight: 600;
    color: var(--text-soft);
    background: var(--surface);
    border: 1px solid var(--border);
    cursor: pointer;
    transition: all 0.15s;
    &:hover {
      color: var(--brand);
      border-color: var(--brand);
    }
    &.on {
      color: #fff;
      background: var(--brand);
      border-color: var(--brand);
    }
  }
}

.rank-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(360px, 100%), 1fr));
  gap: 10px 14px;
}
.rank-skeleton {
  height: 94px;
  border-radius: var(--radius);
  background: var(--surface);
  border: 1px solid var(--border);
  animation: pulse 1.4s ease-in-out infinite;
}
.rank-row {
  display: flex;
  align-items: center;
  gap: 16px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 12px 16px;
  cursor: pointer;
  transition: all 0.15s;
  &:hover {
    border-color: var(--brand);
    box-shadow: var(--shadow-s);
  }
}
.no {
  flex-shrink: 0;
  width: 34px;
  text-align: center;
  font-size: 20px;
  font-weight: 800;
  font-style: italic;
  color: var(--text-muted);
  &.top {
    color: var(--accent);
  }
}
.thumb {
  flex-shrink: 0;
  width: 52px;
  height: 70px;
  border-radius: 8px;
  overflow: hidden;
  background: var(--surface-2);
}
.main {
  flex: 1;
  min-width: 0;
  .title {
    margin: 0 0 5px;
    font-size: 15px;
    font-weight: 600;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .meta {
    margin: 0 0 6px;
    font-size: 12px;
    color: var(--text-muted);
    .rating {
      color: var(--rating);
      font-weight: 700;
      margin-right: 6px;
    }
  }
  .tags {
    display: flex;
    gap: 5px;
    flex-wrap: wrap;
    .tag {
      font-size: 11px;
      color: var(--text-soft);
      background: var(--surface-2);
      padding: 1px 7px;
      border-radius: 5px;
    }
  }
}
@media (max-width: 600px) {
  .rank-row {
    gap: 10px;
    padding: 10px 12px;
  }
  .no {
    width: 24px;
    font-size: 16px;
  }
  .main .tags {
    display: none;
  }
}
</style>
