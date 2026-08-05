<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/layout/PageHeader.vue'
import { fetchConfig, updateConfig, uploadAdminImage, type SysConfigItem } from '@/api/config'

const ORDER_KEY = 'pan.display.order'
const DISPLAY_PREFIX = 'pan.display.'

const loading = ref(false)
const saving = ref(false)
const items = ref<SysConfigItem[]>([])
const original = ref<Record<string, string>>({})
const model = ref<Record<string, string>>({})
const activeGroup = ref('')

const groups = computed(() => {
  const map = new Map<string, SysConfigItem[]>()
  for (const it of items.value) {
    // 网盘展示组：开关项由下方排序列表统一渲染，不重复列出来
    if (it.group === '网盘展示' && (it.key === ORDER_KEY || it.key.startsWith(DISPLAY_PREFIX))) {
      continue
    }
    if (!map.has(it.group)) map.set(it.group, [])
    map.get(it.group)!.push(it)
  }
  // 保证「网盘展示」分组始终出现（即使开关被抽走）
  if (items.value.some((it) => it.group === '网盘展示') && !map.has('网盘展示')) {
    map.set('网盘展示', [])
  }
  return Array.from(map.entries()).map(([name, list]) => ({ name, list }))
})

const activeList = computed(() => groups.value.find((g) => g.name === activeGroup.value)?.list || [])

const isPanGroup = computed(() => activeGroup.value === '网盘展示')

/** slug → 中文 label（来自各 BOOL 配置项）。 */
const panLabelBySlug = computed(() => {
  const m: Record<string, string> = {}
  for (const it of items.value) {
    if (it.key.startsWith(DISPLAY_PREFIX) && it.key !== ORDER_KEY) {
      m[it.key.slice(DISPLAY_PREFIX.length)] = it.label
    }
  }
  return m
})

/** 当前排序后的 slug 列表（缺项自动补全）。 */
const panOrder = computed(() => {
  const known = Object.keys(panLabelBySlug.value)
  const raw = (model.value[ORDER_KEY] || '').split(',').map((s) => s.trim()).filter(Boolean)
  const out: string[] = []
  const seen = new Set<string>()
  for (const s of raw) {
    if (known.includes(s) && !seen.has(s)) {
      out.push(s)
      seen.add(s)
    }
  }
  for (const s of known) {
    if (!seen.has(s)) out.push(s)
  }
  return out
})

function setPanOrder(slugs: string[]) {
  model.value[ORDER_KEY] = slugs.join(',')
}

function movePan(index: number, delta: number) {
  const next = [...panOrder.value]
  const j = index + delta
  if (j < 0 || j >= next.length) return
  ;[next[index], next[j]] = [next[j], next[index]]
  setPanOrder(next)
}

function panEnabled(slug: string): boolean {
  return model.value[DISPLAY_PREFIX + slug] === 'true'
}

function setPanEnabled(slug: string, on: boolean) {
  model.value[DISPLAY_PREFIX + slug] = on ? 'true' : 'false'
}

/** 每个分类下未保存的改动数（给左侧导航打角标）。 */
function groupDirtyCount(name: string): number {
  if (name === '网盘展示') {
    let n = 0
    if (model.value[ORDER_KEY] !== original.value[ORDER_KEY]) n++
    for (const it of items.value) {
      if (it.key.startsWith(DISPLAY_PREFIX) && it.key !== ORDER_KEY) {
        if (model.value[it.key] !== original.value[it.key]) n++
      }
    }
    return n
  }
  const list = groups.value.find((g) => g.name === name)?.list || []
  return list.filter((it) => model.value[it.key] !== original.value[it.key]).length
}

watch(groups, (gs) => {
  if (gs.length && !gs.some((g) => g.name === activeGroup.value)) {
    activeGroup.value = gs[0].name
  }
})

const dirty = computed(() => {
  const out: Record<string, string> = {}
  for (const it of items.value) {
    if (model.value[it.key] !== original.value[it.key]) out[it.key] = model.value[it.key]
  }
  return out
})
const dirtyCount = computed(() => Object.keys(dirty.value).length)

function applyItems(list: SysConfigItem[]) {
  items.value = list
  const orig: Record<string, string> = {}
  const m: Record<string, string> = {}
  for (const it of list) {
    orig[it.key] = it.value
    m[it.key] = it.value
  }
  original.value = orig
  model.value = m
}

async function load() {
  loading.value = true
  try {
    applyItems(await fetchConfig())
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function save() {
  if (dirtyCount.value === 0) {
    ElMessage.info('没有改动')
    return
  }
  // 保存前把排序规范化写回（补全缺项）
  if (model.value[ORDER_KEY] !== undefined) {
    setPanOrder(panOrder.value)
  }
  saving.value = true
  try {
    applyItems(await updateConfig(dirty.value))
    ElMessage.success('已保存，立即生效')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

function reset() {
  model.value = { ...original.value }
}

/** ENUM 选项中文标签（未知值原样显示）。 */
function enumLabel(key: string, option: string): string {
  if (key === 'notify.channel') {
    if (option === 'feishu') return '飞书应用（私聊）'
    if (option === 'feishu_bot') return '飞书群机器人'
    if (option === 'wecom') return '企业微信'
  }
  return option
}

const uploadingNotice = ref(false)
const noticePreviewOpen = ref(false)

const noticePreviewTitle = computed(
  () => (model.value['site.notice.title'] || '').trim() || '网站公告',
)
const noticePreviewHtml = computed(() => (model.value['site.notice.content'] || '').trim())

async function onNoticeImage(file: File) {
  uploadingNotice.value = true
  try {
    const url = await uploadAdminImage(file, 'notice')
    const key = 'site.notice.content'
    const cur = model.value[key] || ''
    const tag = `<p><img src="${url}" alt="" /></p>`
    model.value[key] = cur ? `${cur}\n${tag}` : tag
    ElMessage.success('图片已插入公告正文，记得保存')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '上传失败')
  } finally {
    uploadingNotice.value = false
  }
  return false
}

onMounted(load)
</script>

<template>
  <div class="page">
    <PageHeader title="系统配置" description="公告、运维通知、采集、网盘展示等；修改后立即生效">
      <template #extra>
        <el-tag v-if="dirtyCount" type="warning">{{ dirtyCount }} 项未保存</el-tag>
      </template>
    </PageHeader>

    <el-card v-loading="loading" shadow="never" class="block">
      <div class="layout">
        <nav class="side">
          <button
            v-for="g in groups"
            :key="g.name"
            class="side-item"
            :class="{ active: g.name === activeGroup }"
            type="button"
            @click="activeGroup = g.name"
          >
            <span>{{ g.name }}</span>
            <el-badge v-if="groupDirtyCount(g.name)" :value="groupDirtyCount(g.name)" type="warning" />
          </button>
        </nav>

        <section class="content">
          <!-- 网盘展示：开关 + 上下排序 -->
          <template v-if="isPanGroup">
            <p class="pan-hint">
              开关控制前台是否显示该网盘；用 ↑↓ 调整页签顺序（详情页 / 资源搜索共用）。
            </p>
            <div v-for="(slug, idx) in panOrder" :key="slug" class="pan-row">
              <div class="pan-rank">{{ idx + 1 }}</div>
              <div class="pan-meta">
                <div class="label">{{ panLabelBySlug[slug] || slug }}</div>
                <code class="key">{{ DISPLAY_PREFIX }}{{ slug }}</code>
              </div>
              <div class="pan-ops">
                <el-button-group>
                  <el-button size="small" :disabled="idx === 0" @click="movePan(idx, -1)">↑</el-button>
                  <el-button size="small" :disabled="idx === panOrder.length - 1" @click="movePan(idx, 1)">↓</el-button>
                </el-button-group>
                <el-switch
                  :model-value="panEnabled(slug)"
                  @update:model-value="(v: boolean) => setPanEnabled(slug, v)"
                />
              </div>
            </div>
            <el-empty v-if="!panOrder.length" description="暂无网盘配置" />
          </template>

          <!-- 其它分组：原表单 -->
          <template v-else>
            <div v-for="it in activeList" :key="it.key" class="row">
              <div class="meta">
                <div class="label">{{ it.label }}</div>
                <div v-if="it.description" class="desc">{{ it.description }}</div>
                <code class="key">{{ it.key }}</code>
              </div>
              <div class="control" :class="{ wide: it.type === 'TEXTAREA' }">
                <el-switch
                  v-if="it.type === 'BOOL'"
                  :model-value="model[it.key] === 'true'"
                  @update:model-value="(v: boolean) => (model[it.key] = v ? 'true' : 'false')"
                />
                <el-select v-else-if="it.type === 'ENUM'" v-model="model[it.key]" style="width: 200px">
                  <el-option
                    v-for="o in it.options || []"
                    :key="o"
                    :label="enumLabel(it.key, o)"
                    :value="o"
                  />
                </el-select>
                <el-input
                  v-else-if="it.type === 'NUMBER'"
                  v-model="model[it.key]"
                  type="number"
                  style="width: 200px"
                />
                <el-input
                  v-else-if="it.type === 'SECRET'"
                  v-model="model[it.key]"
                  type="password"
                  show-password
                  autocomplete="off"
                  style="width: 320px"
                />
                <div v-else-if="it.type === 'TEXTAREA'" class="textarea-wrap">
                  <el-input
                    v-model="model[it.key]"
                    type="textarea"
                    :rows="8"
                    placeholder="支持 HTML"
                  />
                  <template v-if="it.key === 'site.notice.content'">
                    <div class="upload-row">
                      <el-upload
                        :show-file-list="false"
                        accept="image/*"
                        :disabled="uploadingNotice"
                        :before-upload="onNoticeImage"
                      >
                        <el-button size="small" :loading="uploadingNotice">上传图片插入</el-button>
                      </el-upload>
                      <el-button size="small" @click="noticePreviewOpen = true">预览弹窗</el-button>
                      <span class="upload-tip">本机存储，广州直出；改文即时预览，未保存也会显示</span>
                    </div>
                    <div class="notice-preview-card">
                      <div class="notice-preview-head">
                        <span class="np-icon" aria-hidden="true">📢</span>
                        <strong>{{ noticePreviewTitle }}</strong>
                        <span class="np-badge">实时预览</span>
                      </div>
                      <div v-if="noticePreviewHtml" class="notice-preview-body" v-html="noticePreviewHtml" />
                      <div v-else class="notice-preview-empty">暂无正文</div>
                    </div>
                  </template>
                </div>
                <el-input v-else v-model="model[it.key]" style="width: 260px" />
              </div>
            </div>
            <el-empty v-if="!activeList.length" description="该分类暂无配置项" />
          </template>
        </section>
      </div>

      <div class="actions">
        <el-button :disabled="dirtyCount === 0" @click="reset">还原</el-button>
        <el-button type="primary" :loading="saving" :disabled="dirtyCount === 0" @click="save">
          保存（{{ dirtyCount }}）
        </el-button>
      </div>
    </el-card>

    <el-dialog
      v-model="noticePreviewOpen"
      :title="noticePreviewTitle"
      width="520px"
      append-to-body
      class="notice-preview-dialog"
    >
      <div v-if="noticePreviewHtml" class="notice-preview-body dialog" v-html="noticePreviewHtml" />
      <div v-else class="notice-preview-empty">暂无正文</div>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.block {
  border: 1px solid var(--border);
}

.layout {
  display: flex;
  gap: 20px;
  min-height: 280px;
}

.side {
  flex: 0 0 180px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  border-right: 1px solid var(--border);
  padding-right: 12px;

  .side-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    padding: 10px 12px;
    border: none;
    background: transparent;
    border-radius: 8px;
    cursor: pointer;
    font-size: 0.9rem;
    color: var(--text-soft);
    text-align: left;
    transition: all 0.15s;

    &:hover {
      background: var(--fill, rgba(0, 0, 0, 0.04));
    }

    &.active {
      background: var(--primary-soft, rgba(64, 128, 255, 0.12));
      color: var(--primary, #3b82f6);
      font-weight: 600;
    }
  }
}

.content {
  flex: 1;
  min-width: 0;
}

.pan-hint {
  margin: 0 0 12px;
  font-size: 0.85rem;
  color: var(--text-soft);
}

.pan-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 4px;
  border-bottom: 1px dashed var(--border);

  &:last-child {
    border-bottom: none;
  }
}

.pan-rank {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: var(--fill, rgba(0, 0, 0, 0.04));
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--text-soft);
}

.pan-meta {
  flex: 1;
  min-width: 0;

  .label {
    font-weight: 500;
  }

  .key {
    color: var(--text-muted);
    font-size: 0.74rem;
  }
}

.pan-ops {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 4px;
  border-bottom: 1px dashed var(--border);

  &:last-child {
    border-bottom: none;
  }

  .meta {
    flex: 0 1 280px;

    .label {
      font-weight: 500;
    }

    .desc {
      color: var(--text-soft);
      font-size: 0.82rem;
      margin-top: 2px;
    }

    .key {
      color: var(--text-muted);
      font-size: 0.74rem;
    }
  }

  .control {
    flex-shrink: 0;
    padding-top: 2px;

    &.wide {
      flex: 1;
      min-width: 0;
    }
  }
}

.textarea-wrap {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.upload-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.upload-tip {
  font-size: 0.8rem;
  color: var(--text-muted);
}

.notice-preview-card {
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--surface, #fff);
  overflow: hidden;
}

.notice-preview-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--border);
  font-size: 0.95rem;

  .np-icon {
    font-size: 15px;
    line-height: 1;
  }

  .np-badge {
    margin-left: auto;
    font-size: 0.72rem;
    font-weight: 500;
    color: var(--primary, #3b82f6);
    background: var(--primary-soft, rgba(64, 128, 255, 0.12));
    padding: 2px 8px;
    border-radius: 999px;
  }
}

.notice-preview-body {
  padding: 14px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--text);
  max-height: 320px;
  overflow-y: auto;

  &.dialog {
    max-height: min(60vh, 480px);
    padding: 0;
  }

  :deep(p) {
    margin: 0 0 8px;
  }

  :deep(p:last-child) {
    margin-bottom: 0;
  }

  :deep(a) {
    color: var(--primary, #3b82f6);
    text-decoration: underline;
    word-break: break-all;
  }

  :deep(ul),
  :deep(ol) {
    padding-left: 1.4em;
    margin: 0 0 8px;
  }

  :deep(img) {
    max-width: 100%;
    height: auto;
    border-radius: 8px;
    margin: 8px 0;
  }
}

.notice-preview-empty {
  padding: 24px 14px;
  text-align: center;
  color: var(--text-muted);
  font-size: 0.85rem;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}
</style>
