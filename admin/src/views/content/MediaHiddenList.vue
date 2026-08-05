<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/layout/PageHeader.vue'
import MediaEditDrawer from '@/components/MediaEditDrawer.vue'
import {
  fetchList,
  isSeriesType,
  listPosterUrl,
  seriesSummary,
  typeLabel,
  type AdminMedia,
} from '@/api/media'

const loading = ref(false)
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
      true,
    )
    rows.value = data.records
    total.value = data.total
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
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

onMounted(loadList)
</script>

<template>
  <div class="page">
    <PageHeader
      title="隐藏列表"
      description="前台已隐藏的影视；可在此重新编辑，关闭「前台 → 隐藏」后恢复展示"
    >
      <template #extra>
        <el-tag type="warning">共 {{ total }} 条</el-tag>
      </template>
    </PageHeader>

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
        <el-button @click="loadList">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="rows" stripe class="table">
        <el-table-column label="海报" width="64">
          <template #default="{ row }">
            <img v-if="listPosterUrl(row)" :src="listPosterUrl(row)" class="thumb" alt="" />
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
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
        <el-table-column label="发布" width="72">
          <template #default="{ row }">
            <el-tag :type="pubTagType(row.pubStatus)" size="small">
              {{ pubLabel(row.pubStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !rows.length" description="暂无隐藏条目" />

      <div v-if="total > size" class="pager">
        <el-pagination
          v-model:current-page="page"
          :page-size="size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="loadList"
        />
      </div>
    </el-card>

    <MediaEditDrawer
      v-model:visible="editVisible"
      :media-id="editId"
      @saved="loadList"
    />
  </div>
</template>

<style scoped lang="scss">
.block {
  border: 1px solid var(--border);
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
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
</style>
