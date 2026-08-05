<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/layout/PageHeader.vue'
import {
  fetchDailyList,
  fetchDaily,
  saveDaily,
  deleteDaily,
  patchDaily,
  checkDaily,
  type DailyItem,
  type DailyLinkInput,
} from '@/api/daily'
import { fetchList, typeLabel, listPosterUrl, type AdminMedia } from '@/api/media'
import { PAN_LABEL, MONITOR_STATUS_LABEL } from '@/api/transfer'

const PAN_TYPES = ['quark', 'baidu', 'xunlei']

function fmt(dt?: string) {
  return dt ? dt.replace('T', ' ').slice(0, 19) : '—'
}

// 紧凑时间：MM-DD HH:mm，用于列表列，完整时间戳放 title 悬停。
function fmtShort(dt?: string): string {
  return dt ? dt.replace('T', ' ').slice(5, 16) : '—'
}

// 相对时间：刚刚 / N分钟前 / N小时前 / 昨天 / N天前，超一周回落到日期。
function relTime(dt?: string): string {
  if (!dt) return '—'
  const t = new Date(dt.replace(' ', 'T')).getTime()
  if (Number.isNaN(t)) return '—'
  const diff = Date.now() - t
  const min = Math.floor(diff / 60000)
  if (min < 1) return '刚刚'
  if (min < 60) return `${min}分钟前`
  const hr = Math.floor(min / 60)
  if (hr < 24) return `${hr}小时前`
  const day = Math.floor(hr / 24)
  if (day === 1) return '昨天'
  if (day < 7) return `${day}天前`
  return dt.replace('T', ' ').slice(0, 10)
}

// 今天更新过（当天 0 点之后）：列表用绿点强调。
function updatedToday(dt?: string): boolean {
  if (!dt) return false
  const t = new Date(dt.replace(' ', 'T'))
  if (Number.isNaN(t.getTime())) return false
  const now = new Date()
  return (
    t.getFullYear() === now.getFullYear() &&
    t.getMonth() === now.getMonth() &&
    t.getDate() === now.getDate()
  )
}

const STATUS_LABEL: Record<string, string> = {
  active: '追更中',
  invalid: '源失效',
  paused: '已暂停',
  ended: '已完结',
  none: '未追更',
}
const STATUS_TYPE: Record<string, 'success' | 'danger' | 'info' | 'warning'> = {
  active: 'success',
  invalid: 'danger',
  paused: 'warning',
  ended: 'info',
  none: 'info',
}

const WEEK_NAMES = ['日', '一', '二', '三', '四', '五', '六']

// 追更节奏人话：「每天 18-23点 每30分钟」/「周五 20-23点 每15分钟」；未设=全局。
function scheduleText(row: DailyItem): string {
  if (!row.checkHours) return '全局巡检'
  let days = '每天'
  if (row.checkDays) {
    days = '周' + row.checkDays.split(',').map((d) => WEEK_NAMES[Number(d)] ?? d).join('')
  }
  const iv = row.checkInterval || ''
  return `${days} ${row.checkHours}点${iv ? ` 每${iv}分` : ''}`
}

// ============ 列表 ============
const loading = ref(false)
const rows = ref<DailyItem[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const keyword = ref('')
/** active=追更中（默认） / ended=已完结 / all=全部 */
const endedFilter = ref<'active' | 'ended' | 'all'>('active')

async function load() {
  loading.value = true
  try {
    const ended =
      endedFilter.value === 'active' ? 0 : endedFilter.value === 'ended' ? 1 : undefined
    const data = await fetchDailyList({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
      ended,
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

function onEndedFilter(v: 'active' | 'ended' | 'all') {
  endedFilter.value = v
  page.value = 1
  load()
}

// ============ 集数展示 / 手动编辑 ============
// 纯数字 → 「第X集」；含"."（综艺日期 M.D）→ 「X.Y」；其它原样。
function epText(v?: string): string {
  if (!v) return ''
  return /^\d+$/.test(v) ? `第${v}集` : v
}

const editingEpisodeId = ref<number | null>(null)
const editingEpisodeVal = ref('')

function startEditEpisode(row: DailyItem) {
  editingEpisodeId.value = row.id
  editingEpisodeVal.value = row.manualEpisode || row.latestEpisode || ''
}
function cancelEditEpisode() {
  editingEpisodeId.value = null
  editingEpisodeVal.value = ''
}
async function saveEpisode(row: DailyItem) {
  try {
    await patchDaily(row.id, { manualEpisode: editingEpisodeVal.value.trim() })
    editingEpisodeId.value = null
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  }
}

async function remove(row: DailyItem) {
  try {
    await ElMessageBox.confirm(
      `从每日更新移除「${row.title || row.mediaId}」？\n仅下架该看板条目，不影响影视库与已转存文件。`,
      '移除条目',
      { type: 'warning', confirmButtonText: '移除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await deleteDaily(row.id)
    ElMessage.success('已移除')
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '移除失败')
  }
}

/** 标完结 / 取消完结：剧级一次；完结后停巡检、保我方链，换号时不迁。 */
async function toggleEnded(row: DailyItem) {
  const ending = !(row.ended === 1)
  try {
    if (ending) {
      await ElMessageBox.confirm(
        `将「${row.title || row.mediaId}」标为完结？\n停止追更巡检，保留我方分享链；换号/号满时这部剧不迁移。`,
        '标为完结',
        { type: 'warning', confirmButtonText: '完结', cancelButtonText: '取消' },
      )
    }
  } catch {
    return
  }
  try {
    await patchDaily(row.id, { ended: ending ? 1 : 0 })
    ElMessage.success(ending ? '已标完结，追更已停' : '已取消完结，追更恢复')
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败')
  }
}

// ============ 添加/编辑对话框 ============
const editVisible = ref(false)
const saving = ref(false)
const form = reactive<{
  id?: number
  mediaId?: number
  links: DailyLinkInput[]
  dayMode: 'everyday' | 'custom'
  selectedDays: number[]
  startHour: number
  endHour: number
  checkInterval: number
}>({
  mediaId: undefined,
  links: [],
  dayMode: 'everyday',
  selectedDays: [],
  startHour: 18,
  endHour: 23,
  checkInterval: 30,
})

const INTERVAL_OPTIONS = [5, 10, 15, 30, 60]
// 选中剧的展示信息
const pickedMedia = ref<Pick<AdminMedia, 'id' | 'title' | 'poster' | 'posterThumb' | 'type' | 'year'> | null>(null)

function resetForm() {
  form.id = undefined
  form.mediaId = undefined
  form.links = [{ panType: 'quark', shareUrl: '', sharePwd: '' }]
  form.dayMode = 'everyday'
  form.selectedDays = []
  form.startHour = 18
  form.endHour = 23
  form.checkInterval = 30
  pickedMedia.value = null
  searchKw.value = ''
  searchResults.value = []
  pasteText.value = ''
  pasteInfo.value = ''
  lastAutoTitle = ''
}

function openCreate() {
  resetForm()
  editVisible.value = true
}

async function openEdit(row: DailyItem) {
  resetForm()
  try {
    const full = await fetchDaily(row.id)
    form.id = full.id
    form.mediaId = full.mediaId
    form.links = (full.monitors || []).map((m) => ({
      panType: m.panType,
      shareUrl: m.shareUrl || '',
      sharePwd: '',
    }))
    if (!form.links.length) form.links = [{ panType: 'quark', shareUrl: '', sharePwd: '' }]
    // 回填追更节奏
    if (full.checkHours) {
      const [s, e] = full.checkHours.split('-')
      form.startHour = Number(s) || 18
      form.endHour = Number(e) || 23
      form.checkInterval = full.checkInterval || 30
      if (full.checkDays) {
        form.dayMode = 'custom'
        form.selectedDays = full.checkDays.split(',').map(Number)
      }
    }
    pickedMedia.value = {
      id: full.mediaId,
      title: full.title,
      poster: full.poster,
      type: full.type,
      year: full.year,
    }
    editVisible.value = true
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  }
}

function addLinkRow() {
  form.links.push({ panType: 'quark', shareUrl: '', sharePwd: '' })
}
function removeLinkRow(i: number) {
  form.links.splice(i, 1)
}

async function submitForm() {
  if (!form.mediaId) {
    ElMessage.warning('请先选择要绑定的剧')
    return
  }
  const links = form.links.filter((l) => l.shareUrl.trim())
  if (!links.length) {
    ElMessage.warning('至少填写一条上游分享链')
    return
  }
  saving.value = true
  try {
    await saveDaily({
      id: form.id,
      mediaId: form.mediaId,
      links: links.map((l) => ({
        panType: l.panType,
        shareUrl: l.shareUrl.trim(),
        sharePwd: l.sharePwd?.trim() || undefined,
      })),
      checkDays:
        form.dayMode === 'custom' && form.selectedDays.length
          ? [...form.selectedDays].sort((a, b) => a - b).join(',')
          : undefined,
      checkHours: `${form.startHour}-${form.endHour}`,
      checkInterval: form.checkInterval,
    })
    ElMessage.success('已保存，监控转存将自动开始')
    editVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

// 追更节奏预览（编辑弹窗实时显示）
const schedulePreview = computed(() => {
  let days = '每天'
  if (form.dayMode === 'custom' && form.selectedDays.length) {
    days = '周' + [...form.selectedDays].sort((a, b) => a - b).map((d) => WEEK_NAMES[d]).join('')
  }
  return `${days} ${form.startHour}-${form.endHour}点 每${form.checkInterval}分钟`
})

function toggleDay(idx: number) {
  const i = form.selectedDays.indexOf(idx)
  if (i === -1) form.selectedDays.push(idx)
  else form.selectedDays.splice(i, 1)
}

// ============ 立即检查 ============
const checkingId = ref<number | null>(null)
async function doCheck(row: DailyItem) {
  checkingId.value = row.id
  try {
    const r = await checkDaily(row.id)
    ElMessage.success(r.enqueued > 0 ? `已触发检查（${r.enqueued} 条链）` : '暂无可检查的追更链')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '触发失败')
  } finally {
    checkingId.value = null
  }
}

// ============ 选剧（对话框内搜索）============
const searchKw = ref('')
const searching = ref(false)
const searchResults = ref<AdminMedia[]>([])

async function doSearch() {
  if (!searchKw.value.trim()) {
    searchResults.value = []
    return
  }
  searching.value = true
  try {
    const p = await fetchList(1, 20, undefined, searchKw.value.trim())
    searchResults.value = p.records
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '搜索失败')
  } finally {
    searching.value = false
  }
}

function pickMedia(m: AdminMedia) {
  form.mediaId = m.id
  pickedMedia.value = { id: m.id, title: m.title, poster: m.poster, posterThumb: m.posterThumb, type: m.type, year: m.year }
  searchResults.value = []
  searchKw.value = ''
}

// ============ 粘贴识别（整段分享文本 → 链接 + 剧名，边贴边认）============
const pasteText = ref('')
const pasteInfo = ref('')
let lastAutoTitle = ''

const PAN_PATTERNS: { pan: string; re: RegExp }[] = [
  { pan: 'quark', re: /https?:\/\/pan\.quark\.cn\/s\/[A-Za-z0-9]+/ },
  { pan: 'baidu', re: /https?:\/\/pan\.baidu\.com\/s\/[A-Za-z0-9_-]+(?:\?pwd=[A-Za-z0-9]+)?/ },
  { pan: 'xunlei', re: /https?:\/\/pan\.xunlei\.com\/s\/[A-Za-z0-9_-]+/ },
]

function matchPwd(s: string): string | undefined {
  const m = s.match(/(?:提取码|密码|pwd|口令)[：:\s=]*([A-Za-z0-9]{4})/i)
  return m ? m[1] : undefined
}

function parseLine(line: string): DailyLinkInput | null {
  for (const { pan, re } of PAN_PATTERNS) {
    const m = line.match(re)
    if (!m) continue
    const url = m[0]
    const inUrl = url.match(/[?&]pwd=([A-Za-z0-9]+)/)
    const pwd = inUrl ? inUrl[1] : matchPwd(line)
    return { panType: pan, shareUrl: url, sharePwd: pwd }
  }
  return null
}

function extractTitle(text: string): string | undefined {
  const first = (text.split(/[\r\n]+/).find((l) => l.trim()) || '').trim()
  let m = first.match(/[「『]([^」』]{1,40})[」』]/) || first.match(/《([^》]{1,40})》/)
  if (m) return m[1].trim()
  const cut = first
    .replace(/^我?(?:复制|分享)?/, '')
    .split(/[【\[（(]/)[0]
    .trim()
  return cut && cut.length >= 1 && cut.length <= 40 ? cut : undefined
}

// 边贴边认：文本一变就重新识别，复制多少认多少；重贴一段就换成新的一批。
watch(pasteText, (text) => {
  if (!text || !text.trim()) {
    pasteInfo.value = ''
    return
  }
  const links: DailyLinkInput[] = []
  const seen = new Set<string>()
  let last: DailyLinkInput | null = null
  for (const raw of text.split(/[\r\n]+/)) {
    const line = raw.trim()
    if (!line) continue
    const parsed = parseLine(line)
    if (parsed) {
      const key = parsed.shareUrl.split('?')[0]
      if (!seen.has(key)) {
        seen.add(key)
        links.push(parsed)
        last = parsed
      }
    } else if (last && !last.sharePwd) {
      const pwd = matchPwd(line)
      if (pwd) last.sharePwd = pwd
    }
  }
  if (!links.length) {
    pasteInfo.value = '暂未识别到夸克/百度/迅雷链接'
    return
  }
  form.links = links
  let msg = `已识别 ${links.length} 条链接`
  const title = extractTitle(text)
  if (title && !form.mediaId && title !== lastAutoTitle) {
    lastAutoTitle = title
    searchKw.value = title
    doSearch()
    msg += `，按「${title}」搜索中`
  }
  pasteInfo.value = msg
})

const canSave = computed(() => !!form.mediaId && form.links.some((l) => l.shareUrl.trim()))

onMounted(() => {
  load()
})
</script>

<template>
  <div class="page">
    <PageHeader
      title="每日更新"
      description="追剧看板：选一部剧 + 录入上游分享链，系统用监控号自动创建/更新稳定资源并回写最新集数，前台展示我方分享链。"
    >
      <template #extra>
        <el-button type="primary" @click="openCreate">添加每日更新</el-button>
      </template>
    </PageHeader>

    <el-card shadow="never" class="block">
      <div class="toolbar">
        <el-radio-group :model-value="endedFilter" size="small" @change="(v: string) => onEndedFilter(v as 'active' | 'ended' | 'all')">
          <el-radio-button value="active">追更中</el-radio-button>
          <el-radio-button value="ended">已完结</el-radio-button>
          <el-radio-button value="all">全部</el-radio-button>
        </el-radio-group>
        <el-input v-model="keyword" placeholder="搜剧名" clearable style="width: 220px" @keyup.enter="onSearch" @clear="onSearch" />
        <el-button type="primary" @click="onSearch">搜索</el-button>
        <div class="tools-right">
          <el-button :loading="loading" @click="load">刷新</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" stripe class="table" row-key="id" default-expand-all>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="detail">
              <div
                v-for="(m, i) in row.monitors || []"
                :key="i"
                class="link-line"
                :class="{ dead: m.status === 'invalid' }"
              >
                <span class="pan-cell">
                  <el-tag size="small" effect="plain">{{ PAN_LABEL[m.panType] || m.panType }}</el-tag>
                  <el-tag v-if="m.status === 'invalid'" size="small" type="danger" effect="dark">失效</el-tag>
                </span>
                <el-link
                  v-if="m.shareUrl"
                  :href="m.shareUrl"
                  :title="m.shareUrl"
                  target="_blank"
                  :type="m.status === 'invalid' ? 'danger' : 'primary'"
                  :underline="false"
                  class="share-link"
                >{{ m.shareUrl }}</el-link>
                <span v-else class="dim">—</span>
                <el-link
                  v-if="m.myShareUrl"
                  :href="m.myShareUrl"
                  :title="m.myShareUrl"
                  target="_blank"
                  type="success"
                  :underline="false"
                  class="share-link"
                >{{ m.myShareUrl }}</el-link>
                <span v-else class="dim">—</span>
              </div>
              <el-empty v-if="!(row.monitors && row.monitors.length)" description="暂无追更链" :image-size="48" />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="剧" min-width="240">
          <template #default="{ row }">
            <div class="media-cell">
              <img v-if="row.poster" :src="row.poster" class="thumb" alt="" />
              <span v-else class="thumb empty">—</span>
              <div class="info">
                <div class="t">{{ row.title || `#${row.mediaId}` }}</div>
                <div class="s">{{ typeLabel(row.type) }} · {{ row.year || '—' }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="更新至" width="150">
          <template #default="{ row }">
            <div v-if="editingEpisodeId === row.id" class="ep-edit">
              <el-input
                v-model="editingEpisodeVal"
                size="small"
                placeholder="如 12 / 7.4"
                style="width: 78px"
                @keyup.enter="saveEpisode(row)"
              />
              <el-button link type="primary" size="small" @click="saveEpisode(row)">存</el-button>
              <el-button link size="small" @click="cancelEditEpisode">取消</el-button>
            </div>
            <span v-else class="ep-cell" title="点击手动纠正集数" @click="startEditEpisode(row)">
              <b v-if="row.latestEpisode" class="ep">{{ epText(row.latestEpisode) }}</b>
              <span v-else class="ep-empty">未更新</span>
              <el-tag v-if="row.manualEpisode" size="small" type="warning" effect="plain" class="ml4">手动</el-tag>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="上次更新" width="130">
          <template #default="{ row }">
            <span v-if="row.lastUpdateAt" class="upd" :title="fmt(row.lastUpdateAt)">
              <span class="upd-main">
                <span v-if="updatedToday(row.lastUpdateAt)" class="dot" />
                {{ fmtShort(row.lastUpdateAt) }}
              </span>
              <span class="rel">{{ relTime(row.lastUpdateAt) }}</span>
            </span>
            <span v-else class="dim">从未</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="STATUS_TYPE[row.status || 'none']" effect="plain">
              {{ STATUS_LABEL[row.status || 'none'] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="追更节奏" min-width="160">
          <template #default="{ row }">
            <span class="sched">{{ scheduleText(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :disabled="row.ended === 1"
              :loading="checkingId === row.id"
              @click="doCheck(row)"
            >立即检查</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button
              v-if="row.type !== 'movie' || row.ended !== 1"
              link
              :type="row.ended === 1 ? 'warning' : 'success'"
              @click="toggleEnded(row)"
            >{{ row.ended === 1 ? '取消完结' : '完结' }}</el-button>
            <el-button link type="danger" @click="remove(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !rows.length" description="还没有每日更新，点右上角添加" />

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

    <!-- 添加 / 编辑 -->
    <el-dialog v-model="editVisible" :title="form.id ? '编辑每日更新' : '添加每日更新'" width="640">
      <!-- 0. 粘贴识别（边贴边认） -->
      <div class="paste-box">
        <div class="section-title" style="margin-top: 0">快速粘贴识别（可选）</div>
        <el-input
          v-model="pasteText"
          type="textarea"
          :rows="3"
          placeholder="把整段分享文本粘这里，自动识别夸克/百度/迅雷链接与提取码，并按剧名搜索。复制几套认几套，重贴一段就换成新的。例：&#10;我复制「悬案」【国剧 2026】更04&#10;夸盘：https://pan.quark.cn/s/xxxx&#10;度盘：https://pan.baidu.com/s/xxxx?pwd=8888"
        />
        <div v-if="pasteInfo" class="paste-info">{{ pasteInfo }}</div>
      </div>

      <!-- 1. 选剧 -->
      <div class="section-title">1. 绑定剧</div>
      <div v-if="pickedMedia" class="picked">
        <img v-if="listPosterUrl(pickedMedia)" :src="listPosterUrl(pickedMedia)" class="thumb" alt="" />
        <span v-else class="thumb empty">—</span>
        <div class="info">
          <div class="t">{{ pickedMedia.title }}</div>
          <div class="s">{{ typeLabel(pickedMedia.type) }} · {{ pickedMedia.year || '—' }}</div>
        </div>
        <el-button link type="primary" @click="pickedMedia = null; form.mediaId = undefined">重选</el-button>
      </div>
      <div v-else class="picker">
        <div class="search">
          <el-input v-model="searchKw" placeholder="搜剧名绑定" clearable @keyup.enter="doSearch" @clear="searchResults = []" />
          <el-button type="primary" :loading="searching" @click="doSearch">搜索</el-button>
        </div>
        <div class="results">
          <div v-for="m in searchResults" :key="m.id" class="result" @click="pickMedia(m)">
            <img v-if="listPosterUrl(m)" :src="listPosterUrl(m)" class="thumb" alt="" />
            <span v-else class="thumb empty">—</span>
            <div class="info">
              <div class="t">{{ m.title }}</div>
              <div class="s">{{ typeLabel(m.type) }} · {{ m.year || '—' }}</div>
            </div>
            <el-button link type="primary">选它</el-button>
          </div>
          <el-empty v-if="!searchResults.length" description="搜索并选择要绑定的剧" :image-size="48" />
        </div>
      </div>

      <!-- 2. 上游链 -->
      <div class="section-title">
        2. 上游分享链（可填多套：夸克 / 百度 / 备用，任一挂了其它顶上）
      </div>
      <div v-for="(l, i) in form.links" :key="i" class="link-row">
        <el-select v-model="l.panType" style="width: 92px">
          <el-option v-for="p in PAN_TYPES" :key="p" :label="PAN_LABEL[p]" :value="p" />
        </el-select>
        <el-input v-model="l.shareUrl" placeholder="上游大佬分享链" class="grow" />
        <el-input v-model="l.sharePwd" placeholder="提取码" style="width: 90px" />
        <el-button link type="danger" :disabled="form.links.length <= 1" @click="removeLinkRow(i)">删</el-button>
      </div>
      <el-button link type="primary" class="add-link" @click="addLinkRow">+ 再加一套</el-button>
      <p class="hint">
        录入的是<b>上游源链</b>；系统用该盘监控号做监控转存（首次创建、之后更新），生成<b>我方稳定分享链</b>供前台展示。
      </p>

      <!-- 3. 检查节奏 -->
      <div class="section-title">3. 检查节奏（系统只在这个时段内自动检查更新）</div>
      <div class="sched-form">
        <div class="sched-row">
          <span class="sched-label">检查日</span>
          <el-radio-group v-model="form.dayMode">
            <el-radio-button value="everyday">每天</el-radio-button>
            <el-radio-button value="custom">指定周几</el-radio-button>
          </el-radio-group>
          <div v-if="form.dayMode === 'custom'" class="weekdays">
            <span
              v-for="(name, idx) in WEEK_NAMES"
              :key="idx"
              class="wd"
              :class="{ active: form.selectedDays.includes(idx) }"
              @click="toggleDay(idx)"
            >{{ name }}</span>
          </div>
        </div>
        <div class="sched-row">
          <span class="sched-label">时段</span>
          <el-select v-model="form.startHour" style="width: 100px">
            <el-option v-for="h in 24" :key="h - 1" :label="`${String(h - 1).padStart(2, '0')}:00`" :value="h - 1" />
          </el-select>
          <span class="sep">—</span>
          <el-select v-model="form.endHour" style="width: 100px">
            <el-option v-for="h in 24" :key="h" :label="`${String(h).padStart(2, '0')}:00`" :value="h" />
          </el-select>
        </div>
        <div class="sched-row">
          <span class="sched-label">频率</span>
          <el-select v-model="form.checkInterval" style="width: 130px">
            <el-option v-for="iv in INTERVAL_OPTIONS" :key="iv" :label="iv >= 60 ? '每 1 小时' : `每 ${iv} 分钟`" :value="iv" />
          </el-select>
          <span class="sched-preview">{{ schedulePreview }}</span>
        </div>
      </div>

      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" :disabled="!canSave" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.page {
  padding: 0;
}
.block {
  margin-top: 12px;
  border: 1px solid var(--border);
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.tools-right {
  margin-left: auto;
  display: flex;
  gap: 8px;
}
.table {
  width: 100%;
}
.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
.media-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.thumb {
  width: 40px;
  height: 56px;
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
  min-width: 0;
  .t {
    font-size: 0.88rem;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .s {
    font-size: 0.74rem;
    color: var(--text-muted);
  }
}
.detail {
  padding: 4px 12px;
  line-height: 1.9;
  font-size: 13px;
  color: #555;
  word-break: break-all;
  .dim {
    color: var(--text-muted);
  }
  .link-line {
    display: grid;
    grid-template-columns: 88px minmax(0, 1fr) minmax(0, 1fr);
    align-items: center;
    column-gap: 16px;
    padding: 3px 8px;
    border-radius: 6px;
    &.dead {
      background: var(--el-color-danger-light-9, #fef0f0);
      border-left: 3px solid var(--el-color-danger, #f56c6c);
    }
  }
  .pan-cell {
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }
  .share-link {
    font-size: 13px;
    display: block;
    min-width: 0;
    max-width: 100%;
    :deep(.el-link__inner) {
      display: block;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}
.mr4 {
  margin-right: 4px;
}
.ml4 {
  margin-left: 4px;
}
.ep-cell {
  cursor: pointer;
  .ep {
    color: var(--el-color-success, #67c23a);
    font-size: 0.95rem;
  }
  .ep-empty {
    color: var(--text-muted);
  }
  &:hover .ep,
  &:hover .ep-empty {
    text-decoration: underline dotted;
  }
}
.ep-edit {
  display: flex;
  align-items: center;
  gap: 4px;
}

.section-title {
  font-weight: 600;
  font-size: 0.9rem;
  margin: 14px 0 8px;
  &:first-child {
    margin-top: 0;
  }
}
.paste-box {
  padding: 10px 12px;
  margin-bottom: 6px;
  background: var(--fill, #f7f8fa);
  border: 1px dashed var(--border);
  border-radius: 6px;
  .paste-info {
    margin-top: 6px;
    font-size: 12px;
    color: var(--el-color-primary, #409eff);
  }
}
.picked {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border: 1px solid var(--border);
  border-radius: 6px;
  .info {
    flex: 1;
  }
}
.picker {
  .search {
    display: flex;
    gap: 8px;
    margin-bottom: 8px;
  }
  .results {
    max-height: 220px;
    overflow-y: auto;
    border: 1px solid var(--border);
    border-radius: 6px;
    padding: 4px 8px;
  }
  .result {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 6px 0;
    border-bottom: 1px dashed var(--border);
    cursor: pointer;
    .info {
      flex: 1;
    }
    &:hover {
      background: var(--fill, #fafafa);
    }
  }
}
.link-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  .grow {
    flex: 1;
  }
}
.add-link {
  margin-bottom: 4px;
}
.hint {
  margin: 8px 0 0;
  font-size: 13px;
  color: #909399;
  line-height: 1.6;
}
.upd {
  display: inline-flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.35;
}
.upd .upd-main {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  white-space: nowrap;
}
.upd .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--el-color-success, #67c23a);
  flex-shrink: 0;
}
.upd .rel {
  color: var(--text-muted);
  font-size: 12px;
  white-space: nowrap;
}
.dim {
  color: var(--text-muted);
}
.sched {
  font-size: 0.82rem;
  color: var(--el-color-primary, #409eff);
}
.sched-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.sched-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.sched-label {
  width: 48px;
  font-size: 0.85rem;
  color: var(--text-muted);
}
.sep {
  color: var(--text-muted);
}
.weekdays {
  display: flex;
  gap: 6px;
}
.wd {
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--border);
  border-radius: 6px;
  cursor: pointer;
  font-size: 0.82rem;
  user-select: none;
}
.wd.active {
  background: var(--el-color-primary, #409eff);
  border-color: var(--el-color-primary, #409eff);
  color: #fff;
}
.sched-preview {
  font-size: 0.82rem;
  color: var(--el-color-success, #67c23a);
}
</style>
