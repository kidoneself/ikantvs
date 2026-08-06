<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { importMedia, tmdbSearch, typeLabel, type TmdbCandidate } from '@/api/media'

const props = defineProps<{ initialKeyword?: string }>()
const emit = defineEmits<{ imported: [] }>()

const keyword = ref('')
const searching = ref(false)
const importingId = ref<number | null>(null)
const directImporting = ref(false)
const publish = ref(true)
const results = ref<TmdbCandidate[]>([])

const directForm = ref({
  url: '',
  tmdbId: '' as string | number,
  type: 'movie',
})

function applyInitial(kw?: string) {
  const q = (kw || '').trim()
  if (q) {
    keyword.value = q
    search()
  }
}

onMounted(() => applyInitial(props.initialKeyword))
watch(
  () => props.initialKeyword,
  (kw) => applyInitial(kw),
)

async function search() {
  const q = keyword.value.trim()
  if (!q) {
    ElMessage.warning('请输入片名')
    return
  }
  searching.value = true
  results.value = []
  try {
    results.value = await tmdbSearch(q)
    if (!results.value.length) ElMessage.info('未找到结果')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '搜索失败')
  } finally {
    searching.value = false
  }
}

async function doDirectImport() {
  const url = directForm.value.url.trim()
  const tmdbId =
    directForm.value.tmdbId === '' || directForm.value.tmdbId === null
      ? undefined
      : Number(directForm.value.tmdbId)

  if (!url && (tmdbId == null || Number.isNaN(tmdbId))) {
    ElMessage.warning('请填写链接或 TMDB ID')
    return
  }

  directImporting.value = true
  try {
    const body: Record<string, unknown> = {
      type: directForm.value.type,
      publish: publish.value,
    }
    if (url) body.url = url
    else body.tmdbId = tmdbId

    const m = await importMedia(body as import('@/api/media').ImportBody)
    ElMessage.success(`已入库：${m.title}`)
    directForm.value.url = ''
    directForm.value.tmdbId = ''
    emit('imported')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '采集失败')
  } finally {
    directImporting.value = false
  }
}

async function pick(row: TmdbCandidate) {
  if (!row.tmdbId) return
  importingId.value = row.tmdbId
  try {
    const m = await importMedia({
      tmdbId: row.tmdbId,
      type: row.type || 'movie',
      publish: publish.value,
    })
    ElMessage.success(`已入库：${m.title}`)
    emit('imported')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '入库失败')
  } finally {
    importingId.value = null
  }
}
</script>

<template>
  <div class="media-import">
    <p class="hint">
      粘贴 TMDB 详情页链接，或直接填 TMDB ID；也可按片名搜 TMDB 再选卡片入库。
    </p>

    <div class="direct-row">
      <el-input v-model="directForm.url" placeholder="TMDB 详情页链接" clearable style="flex: 1" />
      <span class="or">或</span>
      <el-input v-model="directForm.tmdbId" placeholder="TMDB ID" style="width: 120px" />
      <el-select v-model="directForm.type" style="width: 100px">
        <el-option label="电影" value="movie" />
        <el-option label="剧集" value="tv" />
        <el-option label="动漫" value="anime" />
        <el-option label="综艺" value="variety" />
      </el-select>
      <el-button type="primary" :loading="directImporting" @click="doDirectImport">采集入库</el-button>
    </div>

    <el-divider content-position="left">或搜 TMDB 片名</el-divider>

    <div class="search-row">
      <el-input
        v-model="keyword"
        placeholder="如：沙丘、繁花、权力的游戏"
        clearable
        style="flex: 1"
        @keyup.enter="search"
      />
      <el-checkbox v-model="publish">入库即发布</el-checkbox>
      <el-button type="primary" plain :loading="searching" @click="search">搜索</el-button>
    </div>

    <div v-if="results.length" class="grid">
      <div v-for="item in results" :key="`${item.type}-${item.tmdbId}`" class="card">
        <img v-if="item.poster" :src="item.poster" class="poster" alt="" />
        <div v-else class="poster empty">无图</div>
        <div class="info">
          <div class="title">{{ item.title }}</div>
          <div class="meta">
            {{ typeLabel(item.type) }} · {{ item.year || '—' }}
            <span v-if="item.rating"> · ★ {{ item.rating }}</span>
          </div>
          <div class="id">TMDB {{ item.tmdbId }}</div>
          <el-button
            size="small"
            type="primary"
            :loading="importingId === item.tmdbId"
            @click="pick(item)"
          >
            采集入库
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.hint {
  margin: 0 0 14px;
  color: var(--text-soft);
  font-size: 0.88rem;
  line-height: 1.5;
}

.direct-row,
.search-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.or {
  color: var(--text-muted);
  font-size: 0.85rem;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.card {
  display: flex;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #fafbfc;
}

.poster {
  width: 56px;
  height: 84px;
  object-fit: cover;
  border-radius: 4px;
  flex-shrink: 0;

  &.empty {
    display: flex;
    align-items: center;
    justify-content: center;
    background: #eee;
    font-size: 12px;
    color: var(--text-muted);
  }
}

.info {
  flex: 1;
  min-width: 0;

  .title {
    font-weight: 600;
    font-size: 0.92rem;
    margin-bottom: 4px;
  }

  .meta,
  .id {
    font-size: 0.8rem;
    color: var(--text-soft);
    margin-bottom: 4px;
  }

  .id {
    color: var(--text-muted);
    margin-bottom: 8px;
  }
}
</style>
