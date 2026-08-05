<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/layout/PageHeader.vue'
import StatCard from '@/components/layout/StatCard.vue'
import { fetchDashboardStats, type DashboardStats } from '@/api/dashboard'
import {
  fetchOverview,
  formatChange,
  changeTone,
  formatCount,
  type AnalyticsOverview,
} from '@/api/analytics'
import { publicSiteBase } from '@/api/media'
import {
  fetchSyncStatus,
  triggerSync,
  type ContentSyncStatus,
  type SyncTask,
} from '@/api/content-sync'

const router = useRouter()
const publicSite = publicSiteBase()
const loading = ref(true)
const stats = ref<DashboardStats | null>(null)
const overview = ref<AnalyticsOverview | null>(null)
const tab = ref<'content' | 'user' | 'site'>('content')

// —— 内容同步 ——
const sync = ref<ContentSyncStatus | null>(null)
const syncSubmitting = ref(false)
let syncTimer: number | undefined

const syncTasks: { key: SyncTask; label: string; hint: string }[] = [
  { key: 'discover', label: '拉新片/热播', hint: '从 TMDB 拉趋势 + 新剧 + 新片入库' },
  { key: 'refresh', label: '刷新连载', hint: '重拉连载剧最新集数/评分/海报' },
  { key: 'rankings', label: '重建榜单', hint: '正在热播 / 最新上映 / 高分推荐' },
]

const syncPercent = computed(() => {
  const s = sync.value
  if (!s) return 0
  if (s.total > 0) return Math.min(100, Math.floor((s.processed / s.total) * 100))
  return s.running ? 0 : 100
})

async function loadSyncStatus() {
  try {
    sync.value = await fetchSyncStatus()
  } catch {
    // 权限不足/未就绪静默降级，不影响概览主体
  }
}

function ensurePolling() {
  if (syncTimer != null) return
  syncTimer = window.setInterval(async () => {
    await loadSyncStatus()
    if (!sync.value?.running) stopPolling()
  }, 1500)
}

function stopPolling() {
  if (syncTimer != null) {
    window.clearInterval(syncTimer)
    syncTimer = undefined
  }
}

async function runSync(task: SyncTask) {
  if (syncSubmitting.value || sync.value?.running) return
  syncSubmitting.value = true
  try {
    sync.value = await triggerSync(task)
    ElMessage.success('已开始，进度实时刷新中')
    ensurePolling()
  } catch (e) {
    ElMessage.error((e as Error).message || '触发失败')
  } finally {
    syncSubmitting.value = false
  }
}

const typeLabels: Record<string, string> = {
  movie: '电影',
  tv: '剧集',
  anime: '动漫',
  variety: '综艺',
}

function typePercent(count: number): number {
  const total = stats.value?.total || 0
  return total ? Math.round((count / total) * 100) : 0
}

/** 人均搜索 = 搜索次数 / 独立访客；无访客时显示 —。 */
const searchesPerVisitor = computed(() => {
  const o = overview.value
  if (!o?.visitors?.current) return '—'
  return (o.searches.current / o.visitors.current).toFixed(1)
})

/** 把求片词带到 TMDB 补录页，方便一键去补。 */
function gotoImport(keyword: string) {
  router.push({ path: '/content/media', query: { tab: 'import', q: keyword } })
}

onMounted(async () => {
  fetchDashboardStats()
    .then((d) => (stats.value = d))
    .finally(() => (loading.value = false))
  // 数据洞察需审核员及以上；权限不足时静默降级，不影响概览主体
  fetchOverview(7)
    .then((d) => (overview.value = d))
    .catch(() => (overview.value = null))
  await loadSyncStatus()
  if (sync.value?.running) ensurePolling()
})

onUnmounted(stopPolling)
</script>

<template>
  <div v-loading="loading" class="dashboard">
    <PageHeader title="概览" description="剧集内容 · 用户访客 · 站点，一屏看全" />

    <el-card shadow="never" class="tabs-card">
      <el-tabs v-model="tab">
        <!-- ==================== 剧集内容 ==================== -->
        <el-tab-pane name="content">
          <template #label><span class="tab-lb"><el-icon><Film /></el-icon> 剧集内容</span></template>

          <section v-if="stats" class="stats-grid">
            <StatCard label="影视总数" :value="stats.total.toLocaleString()" type="primary" icon="Film" />
            <StatCard label="已发布" :value="stats.published.toLocaleString()" type="success" hint="前台可见" icon="View" />
            <StatCard label="草稿" :value="stats.draft.toLocaleString()" type="warning" hint="待补全/发布" icon="EditPen" />
          </section>

          <el-row :gutter="16" class="panels">
            <el-col :xs="24" :md="12">
              <el-card shadow="never" class="panel">
                <template #header><span class="ph">类型分布</span></template>
                <div v-if="stats" class="type-list">
                  <div v-for="(count, type) in stats.byType" :key="type" class="type-row">
                    <span class="ty-name">{{ typeLabels[type] || type }}</span>
                    <div class="bar">
                      <div class="bar-fill" :style="{ width: typePercent(count) + '%' }" />
                    </div>
                    <strong class="ty-num">{{ count.toLocaleString() }}</strong>
                  </div>
                </div>
              </el-card>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-card shadow="never" class="panel">
                <template #header>
                  <span class="ph">👆 热门卡片</span>
                  <span class="ph-sub">近 7 天最多人点进搜资源的剧</span>
                </template>
                <ol v-if="overview && overview.topCardClicked?.length" class="media-list">
                  <li v-for="(m, i) in overview.topCardClicked.slice(0, 8)" :key="m.mediaId">
                    <span class="rk" :class="{ top: i < 3 }">{{ i + 1 }}</span>
                    <img v-if="m.poster" :src="m.poster" class="thumb" alt="" />
                    <span v-else class="thumb empty">—</span>
                    <div class="minfo">
                      <div class="mt">{{ m.title || `#${m.mediaId}` }}</div>
                      <div class="ms">{{ typeLabels[m.type || ''] || m.type }}</div>
                    </div>
                    <span class="cnt">{{ m.cnt }} 次</span>
                  </li>
                </ol>
                <el-empty v-else description="暂无卡片点击" :image-size="56" />
              </el-card>
            </el-col>
          </el-row>

          <el-row :gutter="16" class="panels">
            <el-col :xs="24" :md="12">
              <el-card shadow="never" class="panel">
                <template #header>
                  <span class="ph">🔗 链点击榜</span>
                  <span class="ph-sub">哪些剧的网盘链最被点</span>
                </template>
                <ol v-if="overview && overview.topLinkClicked.length" class="media-list">
                  <li v-for="(m, i) in overview.topLinkClicked.slice(0, 8)" :key="m.mediaId">
                    <span class="rk" :class="{ top: i < 3 }">{{ i + 1 }}</span>
                    <img v-if="m.poster" :src="m.poster" class="thumb" alt="" />
                    <span v-else class="thumb empty">—</span>
                    <div class="minfo">
                      <div class="mt">{{ m.title || `#${m.mediaId}` }}</div>
                      <div class="ms">{{ typeLabels[m.type || ''] || m.type }}</div>
                    </div>
                    <span class="cnt">{{ m.cnt }} 次</span>
                  </li>
                </ol>
                <el-empty v-else description="暂无链点击数据" :image-size="56" />
              </el-card>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-card shadow="never" class="panel">
                <template #header>
                  <span class="ph">🎯 求片榜</span>
                  <span class="ph-sub">用户搜了、站内还没有 — 优先补这些</span>
                </template>
                <ol v-if="overview && overview.demandGaps.length" class="kw-list">
                  <li v-for="(k, i) in overview.demandGaps.slice(0, 8)" :key="k.keyword">
                    <span class="rk" :class="{ top: i < 3 }">{{ i + 1 }}</span>
                    <span class="kw">{{ k.keyword }}</span>
                    <span class="cnt">{{ k.cnt }} 次</span>
                    <el-button link type="primary" size="small" @click="gotoImport(k.keyword)">去补录</el-button>
                  </li>
                </ol>
                <el-empty v-else description="近 7 天没有零结果搜索" :image-size="56" />
              </el-card>
            </el-col>
          </el-row>

          <!-- 内容同步（手动触发，异步执行 + 实时进度） -->
          <section class="sync">
            <div class="sync-head">
              <div>
                <span class="t">内容同步</span>
                <span class="sub">定时之外，随时手动拉新 / 刷新连载 / 重建榜单</span>
              </div>
              <el-tag v-if="sync?.running" type="warning" effect="light" round>
                {{ sync.taskLabel }} 执行中
              </el-tag>
              <el-tag v-else type="success" effect="plain" round>空闲</el-tag>
            </div>

            <div class="sync-actions">
              <button
                v-for="t in syncTasks"
                :key="t.key"
                class="sync-btn"
                type="button"
                :disabled="syncSubmitting || sync?.running"
                :title="t.hint"
                @click="runSync(t.key)"
              >
                <span class="lb">{{ t.label }}</span>
                <span class="hint">{{ t.hint }}</span>
              </button>
            </div>

            <div v-if="sync && (sync.running || sync.result || sync.error)" class="sync-progress">
              <div class="pg-line">
                <span class="pg-task">{{ sync.taskLabel }}</span>
                <span v-if="sync.running" class="pg-phase">
                  {{ sync.phase }}
                  <template v-if="sync.total > 0">· {{ sync.processed }}/{{ sync.total }}</template>
                  <template v-if="sync.affected > 0">· 生效 {{ sync.affected }}</template>
                </span>
                <span v-else-if="sync.result" class="pg-done">✓ {{ sync.result }}</span>
              </div>
              <el-progress
                v-if="sync.running"
                :percentage="syncPercent"
                :indeterminate="sync.total === 0"
                :duration="2"
                :stroke-width="8"
                striped
                striped-flow
              />
              <div v-if="sync.error" class="pg-error">⚠ {{ sync.error }}</div>
            </div>
          </section>
        </el-tab-pane>

        <!-- ==================== 用户访客 ==================== -->
        <el-tab-pane name="user">
          <template #label><span class="tab-lb"><el-icon><User /></el-icon> 用户访客</span></template>

          <template v-if="overview">
            <div class="pane-bar">
              <span class="pane-sub">近 7 天</span>
              <button class="sec-more" type="button" @click="router.push('/analytics')">
                数据洞察 <el-icon><ArrowRight /></el-icon>
              </button>
            </div>

            <section class="stats-grid">
              <StatCard
                label="独立访客"
                :value="formatCount(overview.visitors?.current)"
                type="primary"
                hint="较上 7 天"
                :change="formatChange(overview.visitors)"
                :change-tone="changeTone(overview.visitors)"
                icon="User"
              />
              <StatCard
                label="搜索次数"
                :value="formatCount(overview.searches?.current)"
                type="primary"
                hint="较上 7 天"
                :change="formatChange(overview.searches)"
                :change-tone="changeTone(overview.searches)"
                icon="Search"
              />
              <StatCard
                label="人均搜索"
                :value="searchesPerVisitor"
                type="success"
                hint="搜索次数 / 独立访客"
                icon="DataLine"
              />
              <StatCard
                label="卡片点击"
                :value="formatCount(overview.cardClicks?.current)"
                type="info"
                hint="较上 7 天"
                :change="formatChange(overview.cardClicks)"
                :change-tone="changeTone(overview.cardClicks)"
                icon="Pointer"
              />
              <StatCard
                label="链点击"
                :value="formatCount(overview.linkClicks?.current)"
                type="warning"
                hint="较上 7 天"
                :change="formatChange(overview.linkClicks)"
                :change-tone="changeTone(overview.linkClicks)"
                icon="Link"
              />
            </section>

            <el-card shadow="never" class="panel">
              <template #header>
                <span class="ph">🔥 热搜榜</span>
                <span class="ph-sub">近 7 天用户最常搜的词</span>
              </template>
              <ol v-if="overview.topSearches.length" class="kw-list grid2">
                <li v-for="(k, i) in overview.topSearches.slice(0, 12)" :key="k.keyword">
                  <span class="rk" :class="{ top: i < 3 }">{{ i + 1 }}</span>
                  <span class="kw">{{ k.keyword }}</span>
                  <span class="cnt">{{ k.cnt }} 次</span>
                </li>
              </ol>
              <el-empty v-else description="暂无搜索数据" :image-size="56" />
            </el-card>
          </template>
          <el-empty v-else description="暂无权限查看用户数据（需审核员及以上）" :image-size="70" />
        </el-tab-pane>

        <!-- ==================== 站点 ==================== -->
        <el-tab-pane name="site">
          <template #label><span class="tab-lb"><el-icon><Setting /></el-icon> 站点</span></template>

          <section v-if="stats" class="stats-grid">
            <StatCard
              label="对象存储"
              :value="stats.r2Ready ? 'R2 就绪' : '未配置'"
              type="info"
              hint="海报 CDN · 自动镜像"
              icon="Cloudy"
            />
            <StatCard
              label="发布占比"
              :value="`${stats.published.toLocaleString()} / ${stats.total.toLocaleString()}`"
              type="success"
              hint="前台可见 / 总量"
              icon="View"
            />
            <StatCard label="已下架" :value="stats.offline.toLocaleString()" type="warning" hint="前台不可见" icon="Hide" />
          </section>

          <el-card shadow="never" class="panel">
            <template #header><span class="ph">快捷操作</span></template>
            <div class="quick-grid">
              <button class="quick" type="button" @click="router.push('/content/media')">
                <el-icon><VideoCamera /></el-icon>
                <span>管理影视库</span>
              </button>
              <button
                class="quick"
                type="button"
                @click="router.push({ path: '/content/media', query: { tab: 'import' } })"
              >
                <el-icon><Search /></el-icon>
                <span>采集入库</span>
              </button>
              <button class="quick" type="button" @click="router.push('/analytics')">
                <el-icon><DataLine /></el-icon>
                <span>数据洞察</span>
              </button>
              <a class="quick" :href="publicSite" target="_blank" rel="noopener">
                <el-icon><Monitor /></el-icon>
                <span>打开前台</span>
              </a>
            </div>
          </el-card>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

/* Tab 卡片 */
.tabs-card {
  :deep(.el-tabs__header) {
    margin-bottom: 18px;
  }
  :deep(.el-tabs__item) {
    font-size: 0.96rem;
    font-weight: 600;
  }
}

.tab-lb {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

/* 分区内工具条（右上角） */
.pane-bar {
  display: flex;
  align-items: center;
  margin-bottom: 12px;

  .pane-sub {
    font-size: 0.8rem;
    color: var(--text-muted);
  }
}

.sec-more {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: transparent;
  color: var(--brand);
  font-size: 0.82rem;
  font-weight: 500;
  cursor: pointer;
}

/* 媒体榜（海报 + 标题 + 次数） */
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

  .rk {
    flex-shrink: 0;
    width: 22px;
    text-align: center;
    font-weight: 700;
    font-style: italic;
    color: var(--text-muted);
    &.top {
      color: var(--brand);
    }
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
      background: var(--fill);
      color: var(--text-muted);
    }
  }
  .minfo {
    flex: 1;
    min-width: 0;
    .mt {
      font-size: 0.85rem;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .ms {
      font-size: 0.72rem;
      color: var(--text-muted);
    }
  }
  .cnt {
    flex-shrink: 0;
    font-size: 0.82rem;
    color: var(--text-soft);
  }
}

/* 内容同步 */
.sync {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-s);
  padding: 16px 20px;
  margin-bottom: 16px;
}

.sync-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;

  .t {
    font-weight: 600;
  }
  .sub {
    margin-left: 8px;
    font-size: 0.74rem;
    color: var(--text-muted);
  }
}

.sync-actions {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.sync-btn {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 12px 14px;
  text-align: left;
  border-radius: var(--radius-s);
  border: 1px solid var(--border);
  background: var(--surface-2);
  color: var(--text);
  cursor: pointer;
  transition: all 0.16s ease;

  .lb {
    font-weight: 600;
    font-size: 0.9rem;
  }
  .hint {
    font-size: 0.74rem;
    color: var(--text-muted);
  }

  &:hover:not(:disabled) {
    border-color: var(--brand-soft-2);
    background: var(--brand-soft);
    transform: translateY(-2px);
    box-shadow: var(--shadow-s);
  }
  &:disabled {
    opacity: 0.55;
    cursor: not-allowed;
  }
}

.sync-progress {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed var(--border);
}

.pg-line {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  font-size: 0.84rem;

  .pg-task {
    font-weight: 600;
  }
  .pg-phase {
    color: var(--text-soft);
  }
  .pg-done {
    color: var(--el-color-success, #16a34a);
  }
}

.pg-error {
  margin-top: 8px;
  font-size: 0.82rem;
  color: var(--el-color-danger, #dc2626);
}

@media (max-width: 768px) {
  .sync-actions {
    grid-template-columns: 1fr;
  }
}

.panels {
  margin-bottom: 0;
}

.panel {
  margin-bottom: 16px;
}

.ph {
  font-weight: 600;
}
.ph-sub {
  margin-left: 8px;
  font-size: 0.74rem;
  color: var(--text-muted);
}

/* 关键词榜 */
.kw-list {
  list-style: none;
  margin: 0;
  padding: 0;

  li {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 8px 0;
    border-bottom: 1px dashed var(--border);
    &:last-child {
      border-bottom: 0;
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
      color: var(--brand);
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
    font-size: 0.82rem;
    color: var(--text-soft);
  }
}

.kw-list.grid2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: 28px;
}

@media (max-width: 768px) {
  .kw-list.grid2 {
    grid-template-columns: 1fr;
  }
}

/* 类型分布（带进度条） */
.type-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.type-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 7px 0;

  .ty-name {
    width: 44px;
    flex-shrink: 0;
    font-size: 0.88rem;
  }

  .bar {
    flex: 1;
    height: 8px;
    border-radius: 999px;
    background: var(--fill);
    overflow: hidden;
  }
  .bar-fill {
    height: 100%;
    border-radius: 999px;
    background: linear-gradient(90deg, #818cf8, #4f46e5);
    transition: width 0.4s ease;
  }

  .ty-num {
    width: 56px;
    text-align: right;
    flex-shrink: 0;
    font-variant-numeric: tabular-nums;
    color: var(--text);
  }
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.quick {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px;
  border-radius: var(--radius-s);
  border: 1px solid var(--border);
  background: var(--surface-2);
  color: var(--text);
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.16s ease;

  .el-icon {
    font-size: 18px;
    color: var(--brand);
  }

  &:hover {
    border-color: var(--brand-soft-2);
    background: var(--brand-soft);
    transform: translateY(-2px);
    box-shadow: var(--shadow-s);
  }
}

</style>
