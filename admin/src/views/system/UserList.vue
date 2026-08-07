<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/layout/PageHeader.vue'
import { useAuthStore } from '@/store/auth'
import { ROLE_LABEL, type StaffRole } from '@/config/roles'
import {
  fetchUsers,
  updateUserRole,
  updateUserStatus,
  type AdminUser,
} from '@/api/users'

const auth = useAuthStore()
const selfId = auth.user?.id

const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const keyword = ref('')
const filterRole = ref('')
const filterStatus = ref<number | ''>('')
const rows = ref<AdminUser[]>([])

const STAFF_ROLES: StaffRole[] = ['contributor', 'reviewer', 'admin']

const ROLE_OPTIONS = STAFF_ROLES.map((value) => ({
  value,
  label: ROLE_LABEL[value],
}))

async function load() {
  loading.value = true
  try {
    const data = await fetchUsers({
      page: page.value,
      size: size.value,
      q: keyword.value || undefined,
      role: filterRole.value || undefined,
      status: filterStatus.value === '' ? undefined : Number(filterStatus.value),
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

async function onRoleChange(row: AdminUser, role: string) {
  const prev = row.role
  try {
    await updateUserRole(row.id, role)
    row.role = role
    ElMessage.success(`已将 ${row.username} 设为${ROLE_LABEL[role as keyof typeof ROLE_LABEL] || role}`)
  } catch (e) {
    row.role = prev
    ElMessage.error(e instanceof Error ? e.message : '修改失败')
  }
}

async function toggleBan(row: AdminUser) {
  const ban = row.status !== 1
  try {
    await ElMessageBox.confirm(
      `确定${ban ? '封禁' : '解封'}用户「${row.username}」吗？`,
      ban ? '封禁用户' : '解封用户',
      { type: ban ? 'warning' : 'info' },
    )
  } catch {
    return
  }
  try {
    const u = await updateUserStatus(row.id, ban ? 1 : 0)
    row.status = u.status
    ElMessage.success(ban ? '已封禁' : '已解封')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败')
  }
}

function fmt(dt?: string) {
  return dt ? dt.replace('T', ' ').slice(0, 16) : '—'
}

onMounted(load)
</script>

<template>
  <div class="page">
    <PageHeader title="后台账号" description="仅展示可登录后台的运营账号（录入员 / 审核员 / 管理员）">
      <template #extra>
        <el-tag>共 {{ total }} 人</el-tag>
      </template>
    </PageHeader>

    <el-card shadow="never" class="block">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜用户名 / 昵称"
          clearable
          style="width: 220px"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-select v-model="filterRole" placeholder="角色" clearable style="width: 120px" @change="onSearch">
          <el-option v-for="o in ROLE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="filterStatus" placeholder="状态" clearable style="width: 110px" @change="onSearch">
          <el-option label="正常" :value="0" />
          <el-option label="已封禁" :value="1" />
        </el-select>
        <el-button type="primary" @click="onSearch">搜索</el-button>
        <div class="tools-right">
          <el-button @click="load">刷新</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" stripe class="table">
        <el-table-column prop="id" label="ID" width="64" />
        <el-table-column label="账号" min-width="160">
          <template #default="{ row }">
            <div class="user-cell">
              <strong>{{ row.username }}</strong>
              <span v-if="row.nickname && row.nickname !== row.username" class="nick">{{ row.nickname }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="130">
          <template #default="{ row }">
            <el-select
              :model-value="row.role"
              size="small"
              :disabled="row.id === selfId"
              @change="(v: string) => onRoleChange(row, v)"
            >
              <el-option v-for="o in ROLE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'danger' : 'success'" size="small">
              {{ row.status === 1 ? '已封禁' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近登录" width="150">
          <template #default="{ row }">{{ fmt(row.lastLoginAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              :type="row.status === 1 ? 'success' : 'danger'"
              :disabled="row.id === selfId"
              @click="toggleBan(row)"
            >
              {{ row.status === 1 ? '解封' : '封禁' }}
            </el-button>
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
  }
}

.user-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  .nick {
    font-size: 12px;
    color: var(--text-muted);
  }
}

.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
