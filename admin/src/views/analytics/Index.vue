<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/layout/PageHeader.vue'
import {
  fetchOverview,
  formatChange,
  changeTone,
  formatCount,
  type AnalyticsOverview,
} from '@/api/analytics'
import { typeLabel } from '@/api/media'

const loading = ref(false)
const days = ref(7)
const data = ref<AnalyticsOverview | null>(null)

const DAY_OPTIONS = [
  { label: '近 7 天', value: 7 },
  { label: '近 30 天', value: 30 },
  { label: '近 90 天', value: 90 },
]

async function load() {
  loading.value = true
  try {
    data.value = await fetchOverview(days.value)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function changeDays(d: number) {
  if (days.value === d) return
  days.value = d
  load()
}

onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <PageHeader title="数据洞察" description="用户在搜什么、想要什么没有、最爱点什么——运营决策的一手数据">
      <template #extra>
        <el-radio-group :model-value="days" @change="(v: number) => changeDays(v)">
          <el-radio-button v-for="o in DAY_OPTIONS" :key="o.value" :value="o.value">
            {{ o.label }}
          </el-radio-button>
        </el-radio-group>
      </template>
    </PageHeader>

    <template v-if="data">
      <!-- 概览数字（带环比） -->
      <div class="stat-row">
        <el-card shadow="never" class="stat">
          <div class="stat-head">
            <div class="stat-num">{{ formatCount(data.visitors?.current) }}</div>
            <span class="delta" :class="changeTone(data.visitors)">{{ formatChange(data.visitors) }}</span>
          </div>
          <div class="stat-label">独立访客</div>
          <div class="stat-sub">较上 {{ data.days }} 天 · 上期 {{ formatCount(data.visitors?.previous) }}</div>
        </el-card>
        <el-card shadow="never" class="stat">
          <div class="stat-head">
            <div class="stat-num">{{ formatCount(data.searches?.current) }}</div>
            <span class="delta" :class="changeTone(data.searches)">{{ formatChange(data.searches) }}</span>
          </div>
          <div class="stat-label">搜索次数</div>
          <div class="stat-sub">较上 {{ data.days }} 天 · 上期 {{ formatCount(data.searches?.previous) }}</div>
        </el-card>
        <el-card shadow="never" class="stat">
          <div class="stat-head">
            <div class="stat-num">{{ formatCount(data.cardClicks?.current) }}</div>
            <span class="delta" :class="changeTone(data.cardClicks)">{{ formatChange(data.cardClicks) }}</span>
          </div>
          <div class="stat-label">卡片点击</div>
          <div class="stat-sub">较上 {{ data.days }} 天 · 上期 {{ formatCount(data.cardClicks?.previous) }}</div>
        </el-card>
        <el-card shadow="never" class="stat">
          <div class="stat-head">
            <div class="stat-num">{{ formatCount(data.linkClicks?.current) }}</div>
            <span class="delta" :class="changeTone(data.linkClicks)">{{ formatChange(data.linkClicks) }}</span>
          </div>
          <div class="stat-label">链点击</div>
          <div class="stat-sub">较上 {{ data.days }} 天 · 上期 {{ formatCount(data.linkClicks?.previous) }}</div>
        </el-card>
      </div>

      <div class="grid">
        <!-- 热搜榜 -->
        <el-card shadow="never" class="block">
          <template #header><span class="b-title">🔥 热搜榜</span></template>
          <ol class="kw-list" v-if="data.topSearches.length">
            <li v-for="(k, i) in data.topSearches" :key="k.keyword">
              <span class="rk" :class="{ top: i < 3 }">{{ i + 1 }}</span>
              <span class="kw">{{ k.keyword }}</span>
              <span class="cnt">{{ k.cnt }}</span>
            </li>
          </ol>
          <el-empty v-else description="暂无搜索数据" :image-size="60" />
        </el-card>

        <!-- 求片榜 -->
        <el-card shadow="never" class="block">
          <template #header>
            <span class="b-title">🎯 求片榜</span>
            <span class="b-sub">搜了但站内没有，优先补这些</span>
          </template>
          <ol class="kw-list" v-if="data.demandGaps.length">
            <li v-for="(k, i) in data.demandGaps" :key="k.keyword">
              <span class="rk gap" :class="{ top: i < 3 }">{{ i + 1 }}</span>
              <span class="kw">{{ k.keyword }}</span>
              <span class="cnt">{{ k.cnt }}</span>
            </li>
          </ol>
          <el-empty v-else description="暂无零结果搜索" :image-size="60" />
        </el-card>

        <!-- 热门卡片 -->
        <el-card shadow="never" class="block">
          <template #header><span class="b-title">👆 热门卡片</span></template>
          <ol class="media-list" v-if="data.topCardClicked?.length">
            <li v-for="(m, i) in data.topCardClicked" :key="m.mediaId">
              <span class="rk" :class="{ top: i < 3 }">{{ i + 1 }}</span>
              <img v-if="m.poster" :src="m.poster" class="thumb" alt="" />
              <span v-else class="thumb empty">—</span>
              <div class="info">
                <div class="t">{{ m.title || `#${m.mediaId}` }}</div>
                <div class="s">{{ typeLabel(m.type) }}</div>
              </div>
              <span class="cnt">{{ m.cnt }}</span>
            </li>
          </ol>
          <el-empty v-else description="暂无卡片点击" :image-size="60" />
        </el-card>

        <!-- 链点击榜 -->
        <el-card shadow="never" class="block">
          <template #header><span class="b-title">🔗 链点击榜</span></template>
          <ol class="media-list" v-if="data.topLinkClicked.length">
            <li v-for="(m, i) in data.topLinkClicked" :key="m.mediaId">
              <span class="rk" :class="{ top: i < 3 }">{{ i + 1 }}</span>
              <img v-if="m.poster" :src="m.poster" class="thumb" alt="" />
              <span v-else class="thumb empty">—</span>
              <div class="info">
                <div class="t">{{ m.title || `#${m.mediaId}` }}</div>
                <div class="s">{{ typeLabel(m.type) }}</div>
              </div>
              <span class="cnt">{{ m.cnt }}</span>
            </li>
          </ol>
          <el-empty v-else description="暂无链点击数据" :image-size="60" />
        </el-card>
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.stat-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;

  .stat {
    border: 1px solid var(--border);
    text-align: center;
  }
  .stat-head {
    display: flex;
    align-items: baseline;
    justify-content: center;
    gap: 8px;
    flex-wrap: wrap;
  }
  .stat-num {
    font-size: 1.8rem;
    font-weight: 700;
    color: var(--el-color-primary);
  }
  .delta {
    font-size: 0.85rem;
    font-weight: 600;
    &.up {
      color: #16a34a;
    }
    &.down {
      color: #dc2626;
    }
    &.flat {
      color: var(--text-soft);
    }
  }
  .stat-label {
    margin-top: 4px;
    font-size: 0.85rem;
    color: var(--text-muted);
  }
  .stat-sub {
    margin-top: 2px;
    font-size: 0.72rem;
    color: var(--text-soft);
  }
}

.grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.block {
  border: 1px solid var(--border);
  .b-title {
    font-weight: 600;
  }
  .b-sub {
    margin-left: 8px;
    font-size: 0.74rem;
    color: var(--text-muted);
  }
}

.kw-list,
.media-list {
  list-style: none;
  margin: 0;
  padding: 0;

  li {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 7px 0;
    border-bottom: 1px dashed var(--border);
    &:last-child {
      border-bottom: 0;
    }
  }
}

.rk {
  flex-shrink: 0;
  width: 22px;
  text-align: center;
  font-weight: 700;
  font-style: italic;
  color: var(--text-muted);
  &.top {
    color: var(--el-color-primary);
  }
  &.gap.top {
    color: #e6713a;
  }
}

.kw {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.cnt {
  flex-shrink: 0;
  font-weight: 600;
  color: var(--text-soft);
}

.thumb {
  width: 30px;
  height: 42px;
  object-fit: cover;
  border-radius: 4px;
  flex-shrink: 0;
  &.empty {
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--fill, #f2f2f2);
    color: var(--text-muted);
  }
}

.info {
  flex: 1;
  min-width: 0;
  .t {
    font-size: 0.85rem;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .s {
    font-size: 0.72rem;
    color: var(--text-muted);
  }
}

@media (max-width: 1100px) {
  .stat-row {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 900px) {
  .grid,
  .stat-row {
    grid-template-columns: 1fr;
  }
}
</style>
