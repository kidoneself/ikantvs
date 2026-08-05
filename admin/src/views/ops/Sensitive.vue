<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/layout/PageHeader.vue'
import {
  ACTION_LABEL,
  ACTION_TYPE,
  CATEGORY_LABEL,
  createWord,
  deleteWord,
  fetchWords,
  importBatch,
  testText,
  updateWord,
  type SensitiveCheckResult,
  type SensitiveWord,
} from '@/api/sensitive'

const CATEGORIES = Object.keys(CATEGORY_LABEL)
const ACTIONS = Object.keys(ACTION_LABEL)

const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const keyword = ref('')
const filterCategory = ref('')
const filterAction = ref('')
const rows = ref<SensitiveWord[]>([])

async function load() {
  loading.value = true
  try {
    const data = await fetchWords({
      page: page.value,
      size: size.value,
      category: filterCategory.value || undefined,
      action: filterAction.value || undefined,
      keyword: keyword.value || undefined,
    })
    rows.value = data.records
    total.value = data.total
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  load()
}

// ---- 新增 / 编辑 ----
const editDialog = ref(false)
const editing = ref<SensitiveWord | null>(null)
const form = reactive({ word: '', category: 'other', action: 'block', enabled: true, remark: '' })

function openCreate() {
  editing.value = null
  Object.assign(form, { word: '', category: 'other', action: 'block', enabled: true, remark: '' })
  editDialog.value = true
}

function openEdit(row: SensitiveWord) {
  editing.value = row
  Object.assign(form, {
    word: row.word,
    category: row.category,
    action: row.action,
    enabled: row.enabled,
    remark: row.remark || '',
  })
  editDialog.value = true
}

async function submitEdit() {
  if (!form.word.trim()) {
    ElMessage.warning('词不能为空')
    return
  }
  try {
    if (editing.value) {
      await updateWord(editing.value.id, { ...form })
      ElMessage.success('已保存')
    } else {
      await createWord({ ...form })
      ElMessage.success('已新增')
    }
    editDialog.value = false
    load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  }
}

async function onToggleEnabled(row: SensitiveWord, val: boolean) {
  try {
    await updateWord(row.id, { ...row, enabled: val })
    row.enabled = val
    ElMessage.success(val ? '已启用' : '已停用')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败')
  }
}

async function onDelete(row: SensitiveWord) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.word}」吗？`, '删除敏感词', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteWord(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

// ---- 批量导入 ----
const batchDialog = ref(false)
const batch = reactive({ text: '', category: 'legacy', action: 'warn' })

function openBatch() {
  Object.assign(batch, { text: '', category: 'legacy', action: 'warn' })
  batchDialog.value = true
}

const batchCount = computed(() => batch.text.split(/\r?\n/).filter((l) => l.trim()).length)

async function submitBatch() {
  if (!batch.text.trim()) {
    ElMessage.warning('请粘贴要导入的词')
    return
  }
  try {
    const added = await importBatch({ ...batch })
    ElMessage.success(`导入完成，新增 ${added} 条（已存在的自动跳过）`)
    batchDialog.value = false
    onSearch()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '导入失败')
  }
}

// ---- 在线测试 ----
const testDialog = ref(false)
const testInput = ref('')
const testResult = ref<SensitiveCheckResult | null>(null)
const testing = ref(false)

function openTest() {
  testInput.value = ''
  testResult.value = null
  testDialog.value = true
}

async function runTest() {
  if (!testInput.value.trim()) {
    ElMessage.warning('请输入要测试的文本')
    return
  }
  testing.value = true
  try {
    testResult.value = await testText(testInput.value)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '测试失败')
  } finally {
    testing.value = false
  }
}

function fmt(dt?: string) {
  return dt ? dt.replace('T', ' ').slice(0, 16) : '—'
}

onMounted(load)
</script>

<template>
  <div class="page">
    <PageHeader title="敏感词" description="用于搜索词拦截与内容展示发布门槛。分级：拦截 / 转审核 / 打码 / 仅标记">
      <template #extra>
        <el-tag>共 {{ total }} 条</el-tag>
      </template>
    </PageHeader>

    <el-card shadow="never" class="block">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜词"
          clearable
          style="width: 200px"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-select v-model="filterCategory" placeholder="分类" clearable style="width: 130px" @change="onSearch">
          <el-option v-for="c in CATEGORIES" :key="c" :label="CATEGORY_LABEL[c]" :value="c" />
        </el-select>
        <el-select v-model="filterAction" placeholder="动作" clearable style="width: 120px" @change="onSearch">
          <el-option v-for="a in ACTIONS" :key="a" :label="ACTION_LABEL[a]" :value="a" />
        </el-select>
        <el-button type="primary" @click="onSearch">搜索</el-button>
        <div class="tools-right">
          <el-button @click="openTest">在线测试</el-button>
          <el-button @click="openBatch">批量导入</el-button>
          <el-button type="primary" @click="openCreate">新增</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" stripe class="table">
        <el-table-column prop="id" label="ID" width="72" />
        <el-table-column prop="word" label="敏感词" min-width="160" />
        <el-table-column label="分类" width="120">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ CATEGORY_LABEL[row.category] || row.category }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="动作" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="ACTION_TYPE[row.action]">{{ ACTION_LABEL[row.action] || row.action }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled" size="small" @change="(v: boolean) => onToggleEnabled(row, v)" />
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column label="更新时间" width="150">
          <template #default="{ row }">{{ fmt(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          :page-size="size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="load"
        />
      </div>
    </el-card>

    <!-- 新增/编辑 -->
    <el-dialog v-model="editDialog" :title="editing ? '编辑敏感词' : '新增敏感词'" width="460">
      <el-form label-width="64px">
        <el-form-item label="词" required>
          <el-input v-model="form.word" placeholder="敏感词" maxlength="64" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" style="width: 100%">
            <el-option v-for="c in CATEGORIES" :key="c" :label="CATEGORY_LABEL[c]" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="动作">
          <el-select v-model="form.action" style="width: 100%">
            <el-option v-for="a in ACTIONS" :key="a" :label="ACTION_LABEL[a]" :value="a" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialog = false">取消</el-button>
        <el-button type="primary" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入 -->
    <el-dialog v-model="batchDialog" title="批量导入敏感词" width="520">
      <p class="hint">一行一个词，重复/已存在的自动跳过。建议迁入老库词时用「历史迁入 + 仅标记」，先观察再升级为拦截。</p>
      <div class="batch-opts">
        <el-select v-model="batch.category" style="width: 140px">
          <el-option v-for="c in CATEGORIES" :key="c" :label="CATEGORY_LABEL[c]" :value="c" />
        </el-select>
        <el-select v-model="batch.action" style="width: 130px">
          <el-option v-for="a in ACTIONS" :key="a" :label="ACTION_LABEL[a]" :value="a" />
        </el-select>
        <span class="count">待导入 {{ batchCount }} 行</span>
      </div>
      <el-input v-model="batch.text" type="textarea" :rows="10" placeholder="每行一个词…" />
      <template #footer>
        <el-button @click="batchDialog = false">取消</el-button>
        <el-button type="primary" @click="submitBatch">导入</el-button>
      </template>
    </el-dialog>

    <!-- 在线测试 -->
    <el-dialog v-model="testDialog" title="在线测试" width="520">
      <el-input
        v-model="testInput"
        type="textarea"
        :rows="4"
        placeholder="粘贴一段文本，看会命中哪些词、触发什么动作…"
      />
      <div class="test-actions">
        <el-button type="primary" :loading="testing" @click="runTest">检测</el-button>
      </div>
      <div v-if="testResult" class="test-result">
        <template v-if="testResult.hit">
          <p>
            命中
            <el-tag :type="ACTION_TYPE[testResult.action || 'warn']" size="small">
              最严动作：{{ ACTION_LABEL[testResult.action || ''] || testResult.action }}
            </el-tag>
          </p>
          <p class="words">
            <el-tag v-for="w in testResult.words" :key="w" size="small" effect="plain" class="w">{{ w }}</el-tag>
          </p>
          <p class="label">打码预览</p>
          <div class="filtered">{{ testResult.filtered }}</div>
        </template>
        <el-result v-else icon="success" title="未命中任何敏感词" sub-title="该文本可正常展示 / 搜索" />
      </div>
    </el-dialog>
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

  .tools-right {
    margin-left: auto;
    display: flex;
    gap: 8px;
  }
}

.table {
  width: 100%;
}

.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.hint {
  margin: 0 0 12px;
  color: var(--text-muted);
  font-size: 0.85rem;
  line-height: 1.5;
}

.batch-opts {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;

  .count {
    color: var(--text-muted);
    font-size: 0.85rem;
  }
}

.test-actions {
  margin: 12px 0;
}

.test-result {
  .words .w {
    margin: 0 6px 6px 0;
  }

  .label {
    margin: 12px 0 6px;
    color: var(--text-soft);
    font-size: 0.85rem;
  }

  .filtered {
    padding: 10px 12px;
    background: var(--fill, #f5f7fa);
    border-radius: 6px;
    word-break: break-all;
    line-height: 1.6;
  }
}
</style>
