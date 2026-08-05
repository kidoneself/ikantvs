<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/layout/PageHeader.vue'
import {
  fetchRankings,
  fetchRanking,
  saveRanking,
  deleteRanking,
  setRankingItems,
  type Ranking,
  type RankingItemMedia,
} from '@/api/ranking'
import { fetchList, typeLabel } from '@/api/media'

const loading = ref(false)
const rows = ref<Ranking[]>([])

async function load() {
  loading.value = true
  try {
    rows.value = await fetchRankings()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

// ---- 新建/编辑榜单 ----
const editVisible = ref(false)
const saving = ref(false)
const form = ref<{ id?: number; name: string; slug: string; description: string; sort: number; enabled: number }>({
  name: '',
  slug: '',
  description: '',
  sort: 0,
  enabled: 1,
})

function openCreate() {
  form.value = { name: '', slug: '', description: '', sort: 0, enabled: 1 }
  editVisible.value = true
}
function openEdit(r: Ranking) {
  form.value = {
    id: r.id,
    name: r.name,
    slug: r.slug,
    description: r.description || '',
    sort: r.sort,
    enabled: r.enabled,
  }
  editVisible.value = true
}
async function submitForm() {
  if (!form.value.name.trim() || !form.value.slug.trim()) {
    ElMessage.warning('名称和 slug 必填')
    return
  }
  saving.value = true
  try {
    await saveRanking(form.value)
    ElMessage.success('已保存')
    editVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function toggleEnabled(r: Ranking) {
  try {
    await saveRanking({ ...r, enabled: r.enabled === 1 ? 0 : 1 })
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败')
  }
}

async function remove(r: Ranking) {
  try {
    await ElMessageBox.confirm(`删除榜单「${r.name}」？条目关联一并删除。`, '删除榜单', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteRanking(r.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

// ---- 条目管理 ----
const itemsVisible = ref(false)
const itemsSaving = ref(false)
const curRanking = ref<Ranking | null>(null)
const editItems = ref<RankingItemMedia[]>([])

const searchKw = ref('')
const searching = ref(false)
const searchResults = ref<RankingItemMedia[]>([])

async function openItems(r: Ranking) {
  curRanking.value = r
  searchKw.value = ''
  searchResults.value = []
  try {
    const full = await fetchRanking(r.id)
    editItems.value = full.items ? [...full.items] : []
    itemsVisible.value = true
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载条目失败')
  }
}

async function doSearch() {
  if (!searchKw.value.trim()) {
    searchResults.value = []
    return
  }
  searching.value = true
  try {
    const page = await fetchList(1, 20, undefined, searchKw.value.trim())
    searchResults.value = page.records.map((m) => ({
      id: m.id,
      title: m.title,
      poster: m.poster,
      type: m.type,
      year: m.year,
      pubStatus: m.pubStatus,
    }))
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '搜索失败')
  } finally {
    searching.value = false
  }
}

function addItem(m: RankingItemMedia) {
  if (editItems.value.some((x) => x.id === m.id)) {
    ElMessage.info('已在榜单中')
    return
  }
  editItems.value.push(m)
}
function removeItem(i: number) {
  editItems.value.splice(i, 1)
}
function moveUp(i: number) {
  if (i <= 0) return
  const arr = editItems.value
  ;[arr[i - 1], arr[i]] = [arr[i], arr[i - 1]]
}
function moveDown(i: number) {
  const arr = editItems.value
  if (i >= arr.length - 1) return
  ;[arr[i + 1], arr[i]] = [arr[i], arr[i + 1]]
}

async function saveItems() {
  if (!curRanking.value) return
  itemsSaving.value = true
  try {
    await setRankingItems(
      curRanking.value.id,
      editItems.value.map((x) => x.id),
    )
    ElMessage.success('榜单条目已保存')
    itemsVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    itemsSaving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <PageHeader title="榜单" description="策划榜单：建榜、加片、调序、上下架；前台展示已上架榜单">
      <template #extra>
        <el-button type="primary" @click="openCreate">新建榜单</el-button>
      </template>
    </PageHeader>

    <el-card shadow="never" class="block">
      <el-table v-loading="loading" :data="rows" stripe class="table">
        <el-table-column prop="name" label="榜单" min-width="140" />
        <el-table-column prop="slug" label="slug" width="120" />
        <el-table-column prop="description" label="说明" min-width="160" show-overflow-tooltip />
        <el-table-column prop="sort" label="排序" width="70" />
        <el-table-column prop="itemCount" label="条目数" width="80" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled === 1"
              @change="() => toggleEnabled(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openItems(row)">管理条目</el-button>
            <el-button link @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !rows.length" description="还没有榜单，点右上角新建" />
    </el-card>

    <!-- 新建/编辑 -->
    <el-dialog v-model="editVisible" :title="form.id ? '编辑榜单' : '新建榜单'" width="460">
      <el-form label-width="72px">
        <el-form-item label="名称"><el-input v-model="form.name" placeholder="如 本周热门" /></el-form-item>
        <el-form-item label="slug"><el-input v-model="form.slug" placeholder="如 weekly-hot" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" placeholder="副标题，可空" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sort" :step="1" /></el-form-item>
        <el-form-item label="上架"><el-switch :model-value="form.enabled === 1" @change="(v: boolean) => (form.enabled = v ? 1 : 0)" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 条目管理 -->
    <el-drawer v-model="itemsVisible" :title="`管理条目 · ${curRanking?.name || ''}`" size="640px">
      <div class="items-layout">
        <div class="col">
          <h4>榜单内容（{{ editItems.length }}）· 从上到下即名次</h4>
          <div class="list">
            <div v-for="(it, i) in editItems" :key="it.id" class="item">
              <span class="no">{{ i + 1 }}</span>
              <img v-if="it.poster" :src="it.poster" class="thumb" alt="" />
              <span v-else class="thumb empty">—</span>
              <div class="info">
                <div class="t">{{ it.title }}</div>
                <div class="s">{{ typeLabel(it.type) }} · {{ it.year || '—' }}
                  <el-tag v-if="it.pubStatus !== 1" type="info" size="small">未发布</el-tag>
                </div>
              </div>
              <div class="ops">
                <el-button link :disabled="i === 0" @click="moveUp(i)">↑</el-button>
                <el-button link :disabled="i === editItems.length - 1" @click="moveDown(i)">↓</el-button>
                <el-button link type="danger" @click="removeItem(i)">移除</el-button>
              </div>
            </div>
            <el-empty v-if="!editItems.length" description="空榜单，右侧搜索添加" :image-size="60" />
          </div>
        </div>

        <div class="col">
          <h4>添加影视</h4>
          <div class="search">
            <el-input
              v-model="searchKw"
              placeholder="搜标题"
              clearable
              @keyup.enter="doSearch"
              @clear="searchResults = []"
            />
            <el-button type="primary" :loading="searching" @click="doSearch">搜索</el-button>
          </div>
          <div class="list">
            <div v-for="m in searchResults" :key="m.id" class="item">
              <img v-if="m.poster" :src="m.poster" class="thumb" alt="" />
              <span v-else class="thumb empty">—</span>
              <div class="info">
                <div class="t">{{ m.title }}</div>
                <div class="s">{{ typeLabel(m.type) }} · {{ m.year || '—' }}</div>
              </div>
              <el-button link type="primary" :disabled="editItems.some((x) => x.id === m.id)" @click="addItem(m)">
                {{ editItems.some((x) => x.id === m.id) ? '已加' : '加入' }}
              </el-button>
            </div>
            <el-empty v-if="!searchResults.length" description="搜索影视加入榜单" :image-size="60" />
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="itemsVisible = false">取消</el-button>
        <el-button type="primary" :loading="itemsSaving" @click="saveItems">保存条目</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
.block {
  border: 1px solid var(--border);
}
.table {
  width: 100%;
}

.items-layout {
  display: flex;
  gap: 16px;
  height: 100%;

  .col {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;

    h4 {
      margin: 0 0 10px;
      font-size: 0.9rem;
    }
  }
}

.search {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
}

.list {
  flex: 1;
  overflow-y: auto;
}

.item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px dashed var(--border);

  .no {
    width: 20px;
    text-align: center;
    font-weight: 700;
    color: var(--text-muted);
  }

  .thumb {
    width: 34px;
    height: 48px;
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
      font-size: 0.74rem;
      color: var(--text-muted);
    }
  }

  .ops {
    flex-shrink: 0;
  }
}
</style>
