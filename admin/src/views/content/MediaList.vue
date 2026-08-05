<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/layout/PageHeader.vue'
import MediaEditDrawer from '@/components/MediaEditDrawer.vue'
import TmdbSearchPanel from '@/components/TmdbSearchPanel.vue'
import {
  fetchList,
  isSeriesType,
  listPosterUrl,
  refreshMedia,
  seriesSummary,
  storageStatus,
  typeLabel,
  publicSiteBase,
  type AdminMedia,
} from '@/api/media'

const route = useRoute()
const publicSite = publicSiteBase()
const activeTab = ref('list')

const loading = ref(false)
const r2Ready = ref(false)

const page = ref(1)
const size = ref(20)
const total = ref(0)
const filterType = ref('')
const keyword = ref('')
const rows = ref<AdminMedia[]>([])

const editVisible = ref(false)
const editId = ref<number | null>(null)

async function loadList() {
  loading.value = true
  try {
    const data = await fetchList(
      page.value,
      size.value,
      filterType.value || undefined,
      keyword.value || undefined,
    )
    rows.value = data.records
    total.value = data.total
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadStatus() {
  try {
    r2Ready.value = (await storageStatus()).ready
  } catch {
    r2Ready.value = false
  }
}

async function doRefresh(row: AdminMedia) {
  try {
    const m = await refreshMedia(row.id)
    const extra = isSeriesType(m.type) ? `（${seriesSummary(m) || '季未同步'}）` : ''
    ElMessage.success(`已刷新${extra}`)
    await loadList()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '刷新失败')
  }
}

function onFilterChange() {
  page.value = 1
  loadList()
}

function onSearch() {
  page.value = 1
  loadList()
}

function openEdit(row: AdminMedia) {
  editId.value = row.id
  editVisible.value = true
}

function pubLabel(status?: number) {
  if (status === 1) return '已发'
  if (status === 2) return '下架'
  return '草稿'
}

function pubTagType(status?: number) {
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  return 'info'
}

onMounted(() => {
  const tab = route.query.tab as string | undefined
  if (tab === 'tmdb' || tab === 'import') activeTab.value = 'import'
  loadStatus()
  loadList()
})

watch(
  () => route.query.tab,
  (tab) => {
    if (tab === 'list') activeTab.value = 'list'
    else if (tab === 'tmdb' || tab === 'import') activeTab.value = 'import'
  },
)

function onImported() {
  page.value = 1
  loadList()
  activeTab.value = 'list'
}
</script>

<template>
  <div class="page">
    <PageHeader title="影视库" description="元数据采集、编辑与发布；网盘链接在「链接管理」模块">
      <template #extra>
        <el-tag>共 {{ total }} 条</el-tag>
        <el-tag :type="r2Ready ? 'success' : 'info'" style="margin-left: 8px">
          R2 {{ r2Ready ? '就绪' : '未配置' }}
        </el-tag>
      </template>
    </PageHeader>

    <el-tabs v-model="activeTab" class="tabs">
      <el-tab-pane label="库内列表" name="list">
        <el-card shadow="never" class="block">
          <div class="toolbar">
            <el-input
              v-model="keyword"
              placeholder="搜标题"
              clearable
              style="width: 220px"
              @keyup.enter="onSearch"
              @clear="onSearch"
            />
            <el-select
              v-model="filterType"
              placeholder="类型"
              clearable
              style="width: 110px"
              @change="onFilterChange"
            >
              <el-option label="电影" value="movie" />
              <el-option label="剧集" value="tv" />
              <el-option label="动漫" value="anime" />
              <el-option label="综艺" value="variety" />
            </el-select>
            <el-button type="primary" @click="onSearch">搜索</el-button>
            <div class="tools-right">
              <el-button @click="loadList">刷新</el-button>
            </div>
          </div>

          <el-table v-loading="loading" :data="rows" stripe class="table">
            <el-table-column label="海报" width="64">
              <template #default="{ row }">
                <img v-if="listPosterUrl(row)" :src="listPosterUrl(row)" class="thumb" alt="" />
                <span v-else class="no-poster">—</span>
              </template>
            </el-table-column>
            <el-table-column prop="title" label="标题" min-width="140" show-overflow-tooltip />
            <el-table-column label="类型" width="72">
              <template #default="{ row }">{{ typeLabel(row.type) }}</template>
            </el-table-column>
            <el-table-column label="季/连载" width="140" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="isSeriesType(row.type)" class="series-cell">{{ seriesSummary(row) }}</span>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
            <el-table-column prop="year" label="年" width="64" />
            <el-table-column prop="hot" label="热度" width="64" />
            <el-table-column prop="tmdbId" label="TMDB" width="80" />
            <el-table-column label="状态" width="72">
              <template #default="{ row }">
                <el-tag :type="pubTagType(row.pubStatus)" size="small">
                  {{ pubLabel(row.pubStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
                <a :href="`${publicSite}/find?q=${encodeURIComponent(row.title || '')}`" target="_blank" rel="noopener">前台</a>
                <el-button link @click="doRefresh(row)">重采</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <el-pagination
              v-model:current-page="page"
              :page-size="size"
              :total="total"
              layout="total, prev, pager, next"
              @current-change="loadList"
            />
          </div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="采集入库" name="import">
        <el-card shadow="never" class="block">
          <TmdbSearchPanel
            :initial-keyword="(route.query.q as string) || ''"
            @imported="onImported"
          />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <MediaEditDrawer
      v-model:visible="editVisible"
      :media-id="editId"
      @saved="loadList"
    />
  </div>
</template>

<style scoped lang="scss">
.tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 12px;
  }
}

.block {
  border: 1px solid var(--border);
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;

  .tools-right {
    margin-left: auto;
    display: flex;
    gap: 8px;
  }
}

.table {
  width: 100%;
}

.thumb {
  width: 40px;
  height: 56px;
  object-fit: cover;
  border-radius: 4px;
}

.no-poster,
.muted {
  color: var(--text-muted);
}

.series-cell {
  font-size: 0.82rem;
  line-height: 1.35;
}

.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

a {
  margin-right: 8px;
  font-size: 0.88rem;
}
</style>
