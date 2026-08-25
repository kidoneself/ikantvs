<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/layout/PageHeader.vue'
import {
  POOL_STATUS_LABEL,
  POOL_STATUS_TYPE,
  fetchSelfProgress,
  ingestPeer,
  ingestSelf,
  type PoolIngestRow,
} from '@/api/pool'

const route = useRoute()
const selfMode = computed(() => route.meta.mode === 'self')
const title = computed(() => (selfMode.value ? '自营录入' : '同行录入'))
const description = computed(() =>
  selfMode.value
    ? '粘贴标题 + 网盘链接，转到站长片库号后再进搜索。用户拿到的是我方永久分享链。不绑影视库、不追更。'
    : '粘贴标题 + 网盘链接，只入库不转存。搜索能搜到；用户获取链接时用源链做临时转存。不绑影视库、不追更。',
)
const submitLabel = computed(() => (selfMode.value ? '转存并入池' : '入池'))

const text = ref('')
const submitting = ref(false)
const rows = ref<PoolIngestRow[]>([])
const summary = ref('')

let pollTimer: number | null = null

function stopPoll() {
  if (pollTimer != null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

async function submit() {
  if (!text.value.trim()) {
    ElMessage.warning('请粘贴标题和网盘链接')
    return
  }
  stopPoll()
  submitting.value = true
  try {
    const data = selfMode.value ? await ingestSelf(text.value) : await ingestPeer(text.value)
    rows.value = data.rows || []
    summary.value = `新增 ${data.added} · 更新 ${data.updated} · 跳过 ${data.skipped} · 失败 ${data.failed}`
    if (selfMode.value && rows.value.some((r) => r.status === 'transferring' && r.id)) {
      pollTimer = window.setInterval(pollProgress, 3000)
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '提交失败')
  } finally {
    submitting.value = false
  }
}

async function pollProgress() {
  const pending = rows.value.filter((r) => r.status === 'transferring' && r.id)
  if (pending.length === 0) {
    stopPoll()
    return
  }
  for (const row of pending) {
    try {
      const fresh = await fetchSelfProgress(row.id as number)
      const i = rows.value.findIndex((r) => r.id === row.id)
      if (i >= 0) {
        rows.value[i] = { ...rows.value[i], ...fresh }
      }
    } catch {
      /* 单条失败不打断轮询 */
    }
  }
}

onUnmounted(stopPoll)

watch(selfMode, () => {
  stopPoll()
  text.value = ''
  rows.value = []
  summary.value = ''
})
</script>

<template>
  <div class="page">
    <PageHeader :title="title" :description="description" />

    <el-card shadow="never" class="block">
      <el-input
        v-model="text"
        type="textarea"
        :rows="14"
        placeholder="「混沌少年时 (2025)」&#10;链接：https://pan.quark.cn/s/xxxx&#10;&#10;下一部标题&#10;https://pan.baidu.com/s/xxxx?pwd=abcd"
      />
      <div class="actions">
        <el-button type="primary" :loading="submitting" @click="submit">{{ submitLabel }}</el-button>
        <span v-if="summary" class="hint">{{ summary }}</span>
      </div>
    </el-card>

    <el-card v-if="rows.length" shadow="never" class="block">
      <el-table :data="rows" stripe>
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column label="盘种" width="80">
          <template #default="{ row }">{{ row.panLabel || row.panType }}</template>
        </el-table-column>
        <el-table-column label="源链" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.url || '—' }}</template>
        </el-table-column>
        <el-table-column v-if="selfMode" label="我方链" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.shareUrl || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="POOL_STATUS_TYPE[row.status] || 'info'">
              {{ POOL_STATUS_LABEL[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="180" show-overflow-tooltip />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.block {
  margin-bottom: 16px;
}
.actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}
.hint {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
