<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  fetchDetail,
  isSeriesType,
  listPosterUrl,
  refreshMedia,
  seriesSummary,
  updateMedia,
  type AdminMedia,
  type MediaUpdateBody,
  type SeasonVO,
} from '@/api/media'

const props = defineProps<{
  visible: boolean
  mediaId: number | null
}>()

const emit = defineEmits<{
  'update:visible': [v: boolean]
  saved: []
}>()

const loading = ref(false)
const saving = ref(false)
const refreshing = ref(false)
const detail = ref<AdminMedia | null>(null)
const seasons = ref<SeasonVO[]>([])

const form = reactive<MediaUpdateBody>({
  title: '',
  overview: '',
  poster: '',
  year: undefined,
  pubStatus: 1,
  hot: 0,
  tier: 0,
  searchHidden: 0,
  tmdbId: undefined as number | undefined,
})

const pubOptions = [
  { label: '草稿', value: 0 },
  { label: '已发布', value: 1 },
  { label: '下架', value: 2 },
]

const tierOptions = [
  { label: '普通', value: 0 },
  { label: '精品', value: 1 },
  { label: '专区', value: 2 },
]

function applyMedia(m: AdminMedia | null | undefined) {
  if (!m) return
  detail.value = m
  form.title = m.title || ''
  form.overview = m.overview || ''
  form.poster = m.poster || ''
  form.year = m.year
  form.pubStatus = m.pubStatus ?? 1
  form.hot = m.hot ?? 0
  form.tier = m.tier ?? 0
  form.searchHidden = m.searchHidden ?? 0
  form.tmdbId = m.tmdbId
}

async function loadDetail(id: number) {
  loading.value = true
  try {
    const res = await fetchDetail(id)
    if (!res.media?.id) {
      ElMessage.error('加载失败：数据格式异常')
      emit('update:visible', false)
      return
    }
    applyMedia(res.media)
    seasons.value = res.seasons ?? []
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
    emit('update:visible', false)
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.visible, props.mediaId] as const,
  ([vis, id]) => {
    if (vis && id) loadDetail(id)
  },
)

function close() {
  emit('update:visible', false)
}

async function save() {
  if (!props.mediaId || !form.title?.trim()) {
    ElMessage.warning('标题不能为空')
    return
  }
  saving.value = true
  try {
    const m = await updateMedia(props.mediaId, {
      ...form,
      title: form.title.trim(),
    })
    applyMedia(m)
    ElMessage.success('已保存')
    emit('saved')
    close()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function doRefresh() {
  if (!props.mediaId) return
  refreshing.value = true
  try {
    await refreshMedia(props.mediaId)
    const res = await fetchDetail(props.mediaId)
    applyMedia(res.media)
    seasons.value = res.seasons ?? []
    const extra = isSeriesType(res.media?.type)
      ? `，${seriesSummary(res.media) || '季未同步'}`
      : ''
    ElMessage.success(`已重采${extra}`)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '重采失败')
  } finally {
    refreshing.value = false
  }
}

function seasonLabel(s: SeasonVO) {
  return s.name ? `第 ${s.seasonNumber} 季 · ${s.name}` : `第 ${s.seasonNumber} 季`
}
</script>

<template>
  <el-drawer
    :model-value="visible"
    title="编辑影视"
    size="560px"
    destroy-on-close
    @close="close"
  >
    <div v-loading="loading" class="body">
      <div v-if="detail" class="meta-bar">
        <el-tag size="small">{{ detail.type }}</el-tag>
        <span v-if="detail.tmdbId" class="muted">TMDB {{ detail.tmdbId }}</span>
        <span v-if="isSeriesType(detail.type)" class="muted">{{ seriesSummary(detail) }}</span>
      </div>

      <el-form label-width="88px" label-position="left">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="年份">
          <el-input-number v-model="form.year" :min="1900" :max="2100" controls-position="right" />
        </el-form-item>
        <el-divider content-position="left">外部 ID</el-divider>
        <el-form-item label="TMDB ID">
          <el-input-number
            v-model="form.tmdbId"
            :min="1"
            controls-position="right"
            placeholder="手动补挂"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="发布">
          <el-select v-model="form.pubStatus" style="width: 100%">
            <el-option v-for="o in pubOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="前台">
          <el-switch
            v-model="form.searchHidden"
            :active-value="1"
            :inactive-value="0"
            active-text="隐藏"
            inactive-text="展示"
          />
          <p class="field-hint">隐藏后前台全不可见（搜索、分类、首页、详情）。后台仍可编辑；永久下架请改「发布」。</p>
        </el-form-item>
        <el-form-item label="热度">
          <el-input-number v-model="form.hot" :min="0" controls-position="right" />
        </el-form-item>
        <el-form-item label="分层">
          <el-select v-model="form.tier" style="width: 100%">
            <el-option v-for="o in tierOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>

        <el-divider content-position="left">海报</el-divider>
        <el-form-item label="海报 URL">
          <el-input v-model="form.poster" type="textarea" :rows="2" placeholder="https://..." />
        </el-form-item>
        <div v-if="detail" class="img-row">
          <div v-if="listPosterUrl(detail)" class="img-box">
            <span class="img-label">列表缩略图</span>
            <img :src="listPosterUrl(detail)" alt="" />
          </div>
          <div v-if="detail.poster" class="img-box">
            <span class="img-label">详情海报</span>
            <img :src="detail.poster" alt="" />
          </div>
          <div v-if="detail.backdrop" class="img-box wide">
            <span class="img-label">背景图</span>
            <img :src="detail.backdrop" alt="" />
          </div>
        </div>

        <el-form-item label="简介">
          <el-input v-model="form.overview" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>

      <template v-if="detail && isSeriesType(detail.type)">
        <el-divider content-position="left">
          季列表
          <span class="muted inline">（TMDB 同步，只读）</span>
        </el-divider>
        <el-table v-if="seasons.length" :data="seasons" size="small" stripe class="season-table">
          <el-table-column label="季" width="48" prop="seasonNumber" />
          <el-table-column label="海报" width="52">
            <template #default="{ row }">
              <img v-if="row.poster" :src="row.poster" class="season-thumb" alt="" />
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="名称" min-width="100" show-overflow-tooltip>
            <template #default="{ row }">{{ seasonLabel(row) }}</template>
          </el-table-column>
          <el-table-column label="集数" width="56" prop="episodeCount" />
          <el-table-column label="首播" width="88" prop="airDate" />
        </el-table>
        <p v-else class="muted empty-seasons">
          {{ detail.seasonCount === 0 ? 'TMDB 无季拆分' : '暂无季数据，可点「重采元数据」' }}
        </p>
      </template>
    </div>
    <template #footer>
      <el-button :loading="refreshing" @click="doRefresh">重采元数据</el-button>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-drawer>
</template>

<style scoped lang="scss">
.body {
  min-height: 200px;
}

.meta-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.muted {
  color: var(--text-muted);
  font-size: 0.82rem;

  &.inline {
    font-weight: normal;
  }
}

.img-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin: 0 0 16px 88px;
}

.img-box {
  display: flex;
  flex-direction: column;
  gap: 4px;

  img {
    max-width: 100px;
    border-radius: 6px;
    border: 1px solid var(--border);
  }

  &.wide img {
    max-width: 200px;
    max-height: 80px;
    object-fit: cover;
  }
}

.img-label {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.season-table {
  width: 100%;
  margin-bottom: 8px;
}

.season-thumb {
  width: 36px;
  height: 52px;
  object-fit: cover;
  border-radius: 4px;
}

.empty-seasons {
  margin: 0 0 12px;
  padding: 8px 0;
}

.field-hint {
  margin: 6px 0 0;
  font-size: 0.78rem;
  color: var(--text-muted);
  line-height: 1.5;

  &.id-hint {
    margin: -8px 0 16px 88px;
  }
}
</style>
