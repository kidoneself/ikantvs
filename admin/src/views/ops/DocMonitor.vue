<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/layout/PageHeader.vue'
import {
  checkAll,
  checkTask,
  createTask,
  deleteTask,
  fetchHistory,
  fetchMeta,
  fetchTasks,
  previewParse,
  setTaskStatus,
  updateTask,
  type CheckResult,
  type DocMonitorTask,
  type HistoryItem,
  type ParseRules,
  type PreviewResult,
} from '@/api/docMonitor'

const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const keyword = ref('')
const filterSource = ref('')
const rows = ref<DocMonitorTask[]>([])
const sources = ref<string[]>([])
const templates = ref<Record<string, ParseRules>>({})

async function loadMeta() {
  try {
    const meta = await fetchMeta()
    sources.value = meta.sources || []
    templates.value = meta.templates || {}
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载元信息失败')
  }
}

async function load() {
  loading.value = true
  try {
    const data = await fetchTasks({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
      source: filterSource.value || undefined,
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

const checkingAll = ref(false)
async function onCheckAll() {
  checkingAll.value = true
  try {
    const list = await checkAll()
    const ok = list.filter((x) => x.success).length
    ElMessage.success(`检查完成：成功 ${ok}/${list.length}`)
    load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '检查失败')
  } finally {
    checkingAll.value = false
  }
}

async function onCheck(row: DocMonitorTask) {
  try {
    const r: CheckResult = await checkTask(row.id)
    if (r.success) {
      ElMessage.success(r.message || '检查完成')
    } else {
      ElMessage.warning(r.message || '检查失败')
    }
    load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '检查失败')
  }
}

async function onToggle(row: DocMonitorTask, val: number | boolean) {
  const enabled = val === 1 || val === true
  try {
    await setTaskStatus(row.id, enabled)
    row.status = enabled ? 1 : 0
  } catch (e) {
    row.status = enabled ? 0 : 1
    ElMessage.error(e instanceof Error ? e.message : '更新状态失败')
  }
}

async function onDelete(row: DocMonitorTask) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.taskName || row.shareUrl}」？`, '删除任务')
    await deleteTask(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e instanceof Error ? e.message : '删除失败')
    }
  }
}

// ---- 编辑弹窗 ----
const dialog = ref(false)
const editing = ref<DocMonitorTask | null>(null)
const saving = ref(false)
const previewing = ref(false)
const preview = ref<PreviewResult | null>(null)

const form = reactive({
  source: 'flowus',
  taskName: '',
  shareUrl: '',
  accessCode: '',
  category: '',
  remark: '',
  template: 'flowus-default',
  quarkPrefixes: '',
  baiduPrefixes: '',
  xunleiPrefixes: '',
  noisePrefixes: '',
  noiseContains: '',
  matchMode: 'startsWith',
  nameExtractRegex: '',
  pwdRegex: '',
})

const templateOptions = computed(() => Object.keys(templates.value))

function splitCsv(s: string): string[] {
  return s
    .split(/[,，\n]/)
    .map((x) => x.trim())
    .filter(Boolean)
}

function joinCsv(arr?: string[]): string {
  return (arr || []).join(', ')
}

function applyTemplate(name: string) {
  const t = templates.value[name]
  if (!t) return
  form.template = name
  form.quarkPrefixes = joinCsv(t.quarkPrefixes)
  form.baiduPrefixes = joinCsv(t.baiduPrefixes)
  form.xunleiPrefixes = joinCsv(t.xunleiPrefixes)
  form.noisePrefixes = joinCsv(t.noisePrefixes)
  form.noiseContains = joinCsv(t.noiseContains)
  form.matchMode = t.matchMode || 'startsWith'
  form.nameExtractRegex = t.nameExtractRegex || ''
  form.pwdRegex = t.pwdRegex || ''
}

function buildRules(): ParseRules {
  return {
    template: form.template || 'custom',
    quarkPrefixes: splitCsv(form.quarkPrefixes),
    baiduPrefixes: splitCsv(form.baiduPrefixes),
    xunleiPrefixes: splitCsv(form.xunleiPrefixes),
    noisePrefixes: splitCsv(form.noisePrefixes),
    noiseContains: splitCsv(form.noiseContains),
    matchMode: form.matchMode,
    nameExtractRegex: form.nameExtractRegex || undefined,
    pwdRegex: form.pwdRegex || undefined,
  }
}

function inferSourceFromUrl(url: string) {
  if (url.includes('kdocs.cn')) {
    form.source = 'kdocs'
    if (!editing.value) applyTemplate('kdocs-default')
  } else if (url.includes('flowus.cn')) {
    form.source = 'flowus'
    if (!editing.value) applyTemplate('flowus-default')
  }
}

function openCreate() {
  editing.value = null
  preview.value = null
  Object.assign(form, {
    source: 'flowus',
    taskName: '',
    shareUrl: '',
    accessCode: '',
    category: '',
    remark: '',
    template: 'flowus-default',
  })
  applyTemplate('flowus-default')
  dialog.value = true
}

function openEdit(row: DocMonitorTask) {
  editing.value = row
  preview.value = null
  form.source = row.source
  form.taskName = row.taskName || ''
  form.shareUrl = row.shareUrl
  form.accessCode = row.accessCode || ''
  form.category = row.category || ''
  form.remark = row.remark || ''
  const r = row.parseRules || {}
  form.template = r.template || 'custom'
  form.quarkPrefixes = joinCsv(r.quarkPrefixes)
  form.baiduPrefixes = joinCsv(r.baiduPrefixes)
  form.xunleiPrefixes = joinCsv(r.xunleiPrefixes)
  form.noisePrefixes = joinCsv(r.noisePrefixes)
  form.noiseContains = joinCsv(r.noiseContains)
  form.matchMode = r.matchMode || 'startsWith'
  form.nameExtractRegex = r.nameExtractRegex || ''
  form.pwdRegex = r.pwdRegex || ''
  dialog.value = true
}

async function onPreview() {
  if (!form.shareUrl.trim()) {
    ElMessage.warning('请先填写分享链接')
    return
  }
  previewing.value = true
  try {
    preview.value = await previewParse({
      source: form.source,
      shareUrl: form.shareUrl.trim(),
      accessCode: form.accessCode || undefined,
      template: form.template,
      parseRules: buildRules(),
    })
    ElMessage.success(`试解析完成：${preview.value.dramaCount} 条剧目，${preview.value.linksCount} 条链`)
  } catch (e) {
    preview.value = null
    ElMessage.error(e instanceof Error ? e.message : '试解析失败')
  } finally {
    previewing.value = false
  }
}

async function onSave() {
  if (!form.shareUrl.trim()) {
    ElMessage.warning('分享链接不能为空')
    return
  }
  if (form.source === 'flowus' && !form.taskName.trim() && !editing.value) {
    // 允许空，后端会回填
  }
  saving.value = true
  try {
    const body = {
      source: form.source,
      taskName: form.taskName.trim(),
      shareUrl: form.shareUrl.trim(),
      accessCode: form.accessCode || undefined,
      category: form.category || undefined,
      remark: form.remark || undefined,
      template: form.template,
      parseRules: buildRules(),
    }
    if (editing.value) {
      await updateTask(editing.value.id, body)
      ElMessage.success('已保存')
    } else {
      await createTask(body)
      ElMessage.success('已创建并检查')
    }
    dialog.value = false
    load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

// ---- 历史 ----
const historyDrawer = ref(false)
const historyLoading = ref(false)
const historyList = ref<HistoryItem[]>([])
const historyTitle = ref('')

async function openHistory(row: DocMonitorTask) {
  historyTitle.value = row.taskName || row.shareUrl
  historyDrawer.value = true
  historyLoading.value = true
  try {
    historyList.value = await fetchHistory(row.id)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载历史失败')
  } finally {
    historyLoading.value = false
  }
}

function sourceLabel(s: string) {
  if (s === 'kdocs') return '金山文档'
  if (s === 'flowus') return 'FlowUs'
  return s
}

function formatTime(t?: string) {
  return t || '-'
}

onMounted(async () => {
  await loadMeta()
  await load()
})
</script>

<template>
  <div class="page">
    <PageHeader
      title="文档采集"
      description="FlowUs / 金山文档监控；每文档可配解析规则，改格式不用发版"
    >
      <template #extra>
        <el-button :loading="checkingAll" @click="onCheckAll">检查全部</el-button>
        <el-button type="primary" @click="openCreate">添加文档</el-button>
      </template>
    </PageHeader>

    <el-card shadow="never" class="filters">
      <el-form inline @submit.prevent="onSearch">
        <el-form-item label="来源">
          <el-select v-model="filterSource" clearable placeholder="全部" style="width: 140px">
            <el-option v-for="s in sources" :key="s" :label="sourceLabel(s)" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="keyword" clearable placeholder="名称 / 分类 / 链接" style="width: 220px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column label="来源" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.source === 'kdocs' ? 'warning' : 'primary'" size="small">
              {{ sourceLabel(row.source) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="taskName" label="任务名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="category" label="分类" width="110" show-overflow-tooltip />
        <el-table-column label="链接" width="80" align="center">
          <template #default="{ row }">{{ row.linksCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="剧目" width="80" align="center">
          <template #default="{ row }">{{ row.dramaCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="格式" width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.parseRules?.template || 'custom' }}</template>
        </el-table-column>
        <el-table-column label="最后更新" width="170">
          <template #default="{ row }">{{ formatTime(row.lastUpdateTime) }}</template>
        </el-table-column>
        <el-table-column label="启用" width="80" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              @change="(v: boolean) => onToggle(row, v)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="onCheck(row)">检查</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="openHistory(row)">历史</el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="load"
          @size-change="load"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="dialog"
      :title="editing ? '编辑文档任务' : '添加文档'"
      width="820px"
      destroy-on-close
      top="5vh"
    >
      <el-form label-width="100px">
        <el-form-item label="来源">
          <el-radio-group
            v-model="form.source"
            @change="(v: string | number | boolean) => applyTemplate(String(v) === 'kdocs' ? 'kdocs-default' : 'flowus-default')"
          >
            <el-radio-button label="flowus">FlowUs</el-radio-button>
            <el-radio-button label="kdocs">金山文档</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="分享链接" required>
          <el-input
            v-model="form.shareUrl"
            :placeholder="form.source === 'kdocs' ? 'https://www.kdocs.cn/l/xxxx' : 'https://flowus.cn/share/...'"
            @blur="inferSourceFromUrl(form.shareUrl)"
          />
        </el-form-item>
        <el-form-item label="任务名称">
          <el-input v-model="form.taskName" placeholder="可空；kdocs 检查后自动取文档标题" />
        </el-form-item>
        <el-form-item v-if="form.source === 'flowus'" label="访问码">
          <el-input v-model="form.accessCode" placeholder="如有" />
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="form.category" placeholder="如：国外剧集" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>

        <el-divider content-position="left">解析格式（本任务专用，改完不用发版）</el-divider>

        <el-form-item label="套用模板">
          <el-select v-model="form.template" style="width: 220px" @change="applyTemplate">
            <el-option v-for="name in templateOptions" :key="name" :label="name" :value="name" />
            <el-option label="custom（当前自定义）" value="custom" />
          </el-select>
        </el-form-item>
        <el-form-item label="匹配模式">
          <el-radio-group v-model="form.matchMode">
            <el-radio label="startsWith">行首前缀（FlowUs）</el-radio>
            <el-radio label="labeled">前缀+冒号且有链（kdocs）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="夸克前缀">
          <el-input v-model="form.quarkPrefixes" placeholder="逗号分隔，如：夸盘, KK, 夸克" />
        </el-form-item>
        <el-form-item label="百度前缀">
          <el-input v-model="form.baiduPrefixes" placeholder="逗号分隔，如：度盘, BD, 百度" />
        </el-form-item>
        <el-form-item label="迅雷前缀">
          <el-input v-model="form.xunleiPrefixes" placeholder="逗号分隔，如：迅雷, 雷盘, XL" />
        </el-form-item>
        <el-form-item label="噪声行首">
          <el-input v-model="form.noisePrefixes" placeholder="以此开头的行跳过" />
        </el-form-item>
        <el-form-item label="噪声包含">
          <el-input v-model="form.noiseContains" placeholder="包含即跳过" />
        </el-form-item>
        <el-form-item label="剧名正则">
          <el-input v-model="form.nameExtractRegex" placeholder="可选，默认书名号" />
        </el-form-item>
        <el-form-item label="提取码正则">
          <el-input v-model="form.pwdRegex" placeholder="可选" />
        </el-form-item>
      </el-form>

      <div v-if="preview" class="preview-box">
        <div class="preview-head">
          试解析：{{ preview.title }} · {{ preview.dramaCount }} 剧 · {{ preview.linksCount }} 链
        </div>
        <el-table :data="preview.dramas || []" size="small" max-height="240">
          <el-table-column prop="name" label="剧名" min-width="120" show-overflow-tooltip />
          <el-table-column prop="fullTitle" label="原标题" min-width="160" show-overflow-tooltip />
          <el-table-column prop="quarkUrl" label="夸克" min-width="140" show-overflow-tooltip />
          <el-table-column prop="baiduUrl" label="百度" min-width="140" show-overflow-tooltip />
          <el-table-column prop="xunleiUrl" label="迅雷" min-width="140" show-overflow-tooltip />
        </el-table>
      </div>

      <template #footer>
        <el-button :loading="previewing" @click="onPreview">试解析</el-button>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="historyDrawer" :title="`更新历史 · ${historyTitle}`" size="50%">
      <el-timeline v-loading="historyLoading">
        <el-timeline-item
          v-for="item in historyList"
          :key="item.id"
          :timestamp="item.createdAt"
          placement="top"
        >
          <el-tag :type="item.hasUpdate === 1 ? 'success' : 'info'" size="small">
            {{ item.hasUpdate === 1 ? '有更新' : '无更新' }}
          </el-tag>
          <span class="hist-msg">{{ item.changeDescription }}</span>
          <div class="hist-meta">{{ item.checkType }} · 链接差 {{ item.linksCountDiff ?? 0 }}</div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-if="!historyLoading && !historyList.length" description="暂无历史" />
    </el-drawer>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.filters {
  margin-bottom: 0;
}
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.preview-box {
  margin-top: 8px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  padding: 10px;
}
.preview-head {
  font-size: 13px;
  margin-bottom: 8px;
  color: var(--el-text-color-secondary);
}
.hist-msg {
  margin-left: 8px;
}
.hist-meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
