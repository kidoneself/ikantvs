<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/layout/PageHeader.vue'
import {
  banIp,
  fetchBlacklist,
  fetchSuspicious,
  unbanIp,
  type BlacklistItem,
  type SuspiciousItem,
} from '@/api/ipGuard'

const loading = ref(false)
const blacklist = ref<BlacklistItem[]>([])
const suspicious = ref<SuspiciousItem[]>([])

async function load() {
  loading.value = true
  try {
    const [bl, sus] = await Promise.all([fetchBlacklist(), fetchSuspicious()])
    blacklist.value = bl
    suspicious.value = sus
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

// 剩余封禁时长人话：-1=永久，否则按时/分/秒。
function ttlText(ttl: number): string {
  if (ttl < 0) return '永久'
  if (ttl >= 3600) return `${Math.floor(ttl / 3600)} 小时 ${Math.floor((ttl % 3600) / 60)} 分`
  if (ttl >= 60) return `${Math.floor(ttl / 60)} 分`
  return `${ttl} 秒`
}

// ---- 手动封禁 ----
const banDialog = ref(false)
const form = reactive({ ip: '', permanent: false, durationHours: 48, reason: '管理员手动封禁' })

function openBan(ip = '') {
  Object.assign(form, { ip, permanent: false, durationHours: 48, reason: '管理员手动封禁' })
  banDialog.value = true
}

async function submitBan() {
  const ip = form.ip.trim()
  if (!ip) {
    ElMessage.warning('请输入要封禁的 IP')
    return
  }
  try {
    await banIp({
      ip,
      permanent: form.permanent,
      durationSeconds: form.permanent ? undefined : Math.max(1, Math.round(form.durationHours * 3600)),
      reason: form.reason?.trim() || '管理员手动封禁',
    })
    ElMessage.success('已封禁')
    banDialog.value = false
    load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '封禁失败')
  }
}

async function onUnban(ip: string) {
  try {
    await ElMessageBox.confirm(`确定解封 ${ip} 吗？`, '解封 IP', { type: 'warning' })
  } catch {
    return
  }
  try {
    await unbanIp(ip)
    ElMessage.success('已解封')
    load()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '解封失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <PageHeader
      title="IP 防护"
      description="限流 + 自动封禁的实时看板。频繁触发限流的 IP 会被自动临时封禁；此处可查看、手动封禁 / 解封。"
    >
      <template #extra>
        <el-button @click="load">刷新</el-button>
        <el-button type="primary" @click="openBan()">手动封禁</el-button>
      </template>
    </PageHeader>

    <el-card shadow="never" class="block">
      <div class="card-title">
        黑名单（当前被封）
        <el-tag size="small" type="danger" effect="plain">{{ blacklist.length }}</el-tag>
      </div>
      <el-table v-loading="loading" :data="blacklist" stripe class="table" empty-text="暂无被封 IP">
        <el-table-column prop="ip" label="IP" min-width="150" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.permanent ? 'danger' : 'warning'">
              {{ row.permanent ? '永久' : '临时' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="剩余时长" width="140">
          <template #default="{ row }">{{ ttlText(row.ttl) }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="240" show-overflow-tooltip />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="onUnban(row.ip)">解封</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="block">
      <div class="card-title">
        可疑 IP（限流命中较多，未封禁）
        <el-tag size="small" type="warning" effect="plain">{{ suspicious.length }}</el-tag>
      </div>
      <el-table :data="suspicious" stripe class="table" empty-text="暂无可疑 IP">
        <el-table-column prop="ip" label="IP" min-width="150" />
        <el-table-column prop="rateLimitCount" label="限流命中次数" width="150" sortable />
        <el-table-column label="窗口剩余" width="120">
          <template #default="{ row }">{{ row.windowTtl != null && row.windowTtl > 0 ? `${row.windowTtl}s` : '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" @click="openBan(row.ip)">立即封禁</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="banDialog" title="手动封禁 IP" width="460">
      <el-form label-width="80px">
        <el-form-item label="IP" required>
          <el-input v-model="form.ip" placeholder="如 1.2.3.4" />
        </el-form-item>
        <el-form-item label="永久封禁">
          <el-switch v-model="form.permanent" />
        </el-form-item>
        <el-form-item v-if="!form.permanent" label="时长(小时)">
          <el-input-number v-model="form.durationHours" :min="0.1" :step="1" :precision="1" />
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="form.reason" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="banDialog = false">取消</el-button>
        <el-button type="primary" @click="submitBan">封禁</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.block {
  margin-top: 12px;
  border: 1px solid var(--border);
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-weight: 600;
}

.table {
  width: 100%;
}
</style>
