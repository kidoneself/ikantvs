<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/layout/PageHeader.vue'
import {
  createSiteDomain,
  deleteSiteDomain,
  fetchPanOptions,
  fetchSiteDomains,
  updateSiteDomain,
  type PanOption,
  type SiteDomainConfig,
} from '@/api/siteDomain'

const loading = ref(false)
const saving = ref(false)
const rows = ref<SiteDomainConfig[]>([])
const panOptions = ref<PanOption[]>([])

const dlg = reactive({
  visible: false,
  isNew: true,
  id: 0,
  host: '',
  enabled: true,
  remark: '',
  pans: {} as Record<string, boolean>,
})

const panLabels = computed(() => panOptions.value)

function emptyPans(allOn: boolean): Record<string, boolean> {
  const m: Record<string, boolean> = {}
  for (const p of panOptions.value) {
    m[p.slug] = allOn
  }
  return m
}

async function load() {
  loading.value = true
  try {
    const [opts, list] = await Promise.all([fetchPanOptions(), fetchSiteDomains()])
    panOptions.value = opts
    rows.value = list
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  dlg.visible = true
  dlg.isNew = true
  dlg.id = 0
  dlg.host = ''
  dlg.enabled = true
  dlg.remark = ''
  dlg.pans = emptyPans(true)
}

function openEdit(row: SiteDomainConfig) {
  dlg.visible = true
  dlg.isNew = false
  dlg.id = row.id
  dlg.host = row.host
  dlg.enabled = row.enabled
  dlg.remark = row.remark || ''
  dlg.pans = { ...emptyPans(false), ...(row.pans || {}) }
}

function presetNaspt() {
  dlg.pans = emptyPans(false)
  dlg.pans.xunlei = true
  dlg.pans.magnet = true
}

function presetAll() {
  dlg.pans = emptyPans(true)
}

async function save() {
  const host = dlg.host.trim()
  if (!host) {
    ElMessage.warning('请填写域名')
    return
  }
  saving.value = true
  try {
    const body = {
      host,
      enabled: dlg.enabled,
      pans: { ...dlg.pans },
      remark: dlg.remark.trim() || undefined,
    }
    if (dlg.isNew) {
      await createSiteDomain(body)
      ElMessage.success('已新增')
    } else {
      await updateSiteDomain(dlg.id, body)
      ElMessage.success('已保存')
    }
    dlg.visible = false
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(row: SiteDomainConfig) {
  try {
    await ElMessageBox.confirm(
      `删除域名「${row.host}」后，该站将回落全局网盘展示配置。确定删除？`,
      '删除域名',
      { type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await deleteSiteDomain(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

function panSummary(row: SiteDomainConfig): string {
  const on = panOptions.value.filter((p) => row.pans?.[p.slug]).map((p) => p.label)
  return on.length ? on.join('、') : '（全关）'
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="page" v-loading="loading">
    <PageHeader
      title="域名网盘"
      description="按访问域名决定前台显示哪些网盘。可新增/删除域名，各盘随意开关。未配置的域名走系统配置里的「网盘展示」。"
    >
      <template #extra>
        <el-button type="primary" @click="openCreate">新增域名</el-button>
      </template>
    </PageHeader>

    <el-table :data="rows" stripe empty-text="暂无域名配置">
      <el-table-column prop="host" label="域名" min-width="160" />
      <el-table-column label="启用" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="已开网盘" min-width="240">
        <template #default="{ row }">
          <span class="pans-text">{{ panSummary(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dlg.visible"
      :title="dlg.isNew ? '新增域名' : '编辑域名'"
      width="560px"
      destroy-on-close
    >
      <el-form label-width="88px">
        <el-form-item label="域名" required>
          <el-input v-model="dlg.host" placeholder="如 naspt.vip（不用写 https / www）" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="dlg.enabled" />
        </el-form-item>
        <el-form-item label="网盘">
          <div class="pan-grid">
            <el-checkbox
              v-for="p in panLabels"
              :key="p.slug"
              v-model="dlg.pans[p.slug]"
            >
              {{ p.label }}
            </el-checkbox>
          </div>
          <div class="presets">
            <el-button size="small" @click="presetNaspt">仅迅雷+磁力</el-button>
            <el-button size="small" @click="presetAll">全开</el-button>
          </div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dlg.remark" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.page {
  padding: 0 4px 24px;
}
.pans-text {
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.pan-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}
.presets {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}
</style>
