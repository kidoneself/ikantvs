<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import PageHeader from '@/components/layout/PageHeader.vue'
import MediaEditDrawer from '@/components/MediaEditDrawer.vue'
import { uploadAdminImage } from '@/api/config'
import {
  createManualMedia,
  fetchList,
  isSeriesType,
  listPosterUrl,
  seriesSummary,
  typeLabel,
  publicSiteBase,
  type AdminMedia,
} from '@/api/media'

const route = useRoute()
const publicSite = publicSiteBase()
const activeTab = ref('list')

const loading = ref(false)
const creating = ref(false)
const uploadingPoster = ref(false)

const page = ref(1)
const size = ref(20)
const total = ref(0)
const filterType = ref('')
const keyword = ref('')
const rows = ref<AdminMedia[]>([])

const editVisible = ref(false)
const editId = ref<number | null>(null)

const form = reactive({
  title: '',
  type: 'movie',
  year: undefined as number | undefined,
  poster: '',
  overview: '',
  publish: true,
})

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

function resetForm() {
  form.title = (route.query.q as string) || ''
  form.type = 'movie'
  form.year = undefined
  form.poster = ''
  form.overview = ''
  form.publish = true
}

async function onUploadPoster(opt: UploadRequestOptions) {
  uploadingPoster.value = true
  try {
    form.poster = await uploadAdminImage(opt.file as File, 'poster')
    ElMessage.success('海报已上传')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '上传失败')
  } finally {
    uploadingPoster.value = false
  }
}

async function submitManual() {
  if (!form.title.trim()) {
    ElMessage.warning('请填写标题')
    return
  }
  creating.value = true
  try {
    await createManualMedia({
      title: form.title.trim(),
      type: form.type,
      year: form.year,
      poster: form.poster.trim() || undefined,
      overview: form.overview.trim() || undefined,
      publish: form.publish,
    })
    ElMessage.success('已入库')
    resetForm()
    page.value = 1
    await loadList()
    activeTab.value = 'list'
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '创建失败')
  } finally {
    creating.value = false
  }
}

onMounted(() => {
  const tab = route.query.tab as string | undefined
  if (tab === 'manual' || tab === 'import' || tab === 'tmdb') activeTab.value = 'manual'
  if (typeof route.query.q === 'string') form.title = route.query.q
  loadList()
})

watch(
  () => route.query.tab,
  (tab) => {
    if (tab === 'list') activeTab.value = 'list'
    else if (tab === 'manual' || tab === 'import' || tab === 'tmdb') activeTab.value = 'manual'
  },
)

watch(
  () => route.query.q,
  (q) => {
    if (typeof q === 'string' && activeTab.value === 'manual') form.title = q
  },
)
</script>

<template>
  <div class="page">
    <PageHeader title="影视库" description="夸克热榜自动灌库；缺片可手工录入">
      <template #extra>
        <el-tag>共 {{ total }} 条</el-tag>
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
              <el-button type="primary" plain @click="activeTab = 'manual'">手工录入</el-button>
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
                <span v-if="isSeriesType(row.type)" class="series-cell">{{ seriesSummary(row) || '—' }}</span>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
            <el-table-column prop="year" label="年" width="64" />
            <el-table-column prop="hot" label="热度" width="64" />
            <el-table-column prop="metaSource" label="来源" width="80" />
            <el-table-column label="状态" width="72">
              <template #default="{ row }">
                <el-tag :type="pubTagType(row.pubStatus)" size="small">
                  {{ pubLabel(row.pubStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="140" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
                <a :href="`${publicSite}/find?q=${encodeURIComponent(row.title || '')}`" target="_blank" rel="noopener">前台</a>
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

      <el-tab-pane label="手工录入" name="manual">
        <el-card shadow="never" class="block">
          <el-form label-width="88px" style="max-width: 560px" @submit.prevent="submitManual">
            <el-form-item label="标题" required>
              <el-input v-model="form.title" placeholder="片名" />
            </el-form-item>
            <el-form-item label="类型">
              <el-select v-model="form.type" style="width: 160px">
                <el-option label="电影" value="movie" />
                <el-option label="剧集" value="tv" />
                <el-option label="动漫" value="anime" />
                <el-option label="综艺" value="variety" />
              </el-select>
            </el-form-item>
            <el-form-item label="年份">
              <el-input-number v-model="form.year" :min="1900" :max="2100" controls-position="right" />
            </el-form-item>
            <el-form-item label="海报">
              <div class="poster-row">
                <el-upload
                  :show-file-list="false"
                  accept="image/*"
                  :http-request="onUploadPoster"
                  :disabled="uploadingPoster"
                >
                  <el-button :loading="uploadingPoster">本地上传</el-button>
                </el-upload>
                <el-input v-model="form.poster" placeholder="或粘贴图片 URL" clearable />
              </div>
              <img v-if="form.poster" :src="form.poster" class="poster-preview" alt="" />
            </el-form-item>
            <el-form-item label="简介">
              <el-input v-model="form.overview" type="textarea" :rows="3" />
            </el-form-item>
            <el-form-item label="发布">
              <el-switch v-model="form.publish" active-text="立即发布" inactive-text="草稿" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="creating" @click="submitManual">入库</el-button>
            </el-form-item>
          </el-form>
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

.poster-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  width: 100%;
  align-items: center;

  .el-input {
    flex: 1;
    min-width: 200px;
  }
}

.poster-preview {
  display: block;
  margin-top: 8px;
  max-width: 120px;
  border-radius: 6px;
  border: 1px solid var(--border);
}
</style>
