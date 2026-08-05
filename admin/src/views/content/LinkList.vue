<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/layout/PageHeader.vue'
import { fetchLinkList, type AdminMediaLink } from '@/api/links'

const route = useRoute()

const loading = ref(false)
const searched = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const rows = ref<AdminMediaLink[]>([])

const keyword = ref('')
const filterPan = ref('')
const filterSource = ref('')
const filterInvalid = ref<number | ''>('')

function hasCriteria() {
  return (
    !!keyword.value.trim() ||
    !!filterPan.value ||
    !!filterSource.value ||
    filterInvalid.value !== '' ||
    !!route.query.mediaId
  )
}

async function loadList() {
  if (!hasCriteria()) {
    rows.value = []
    total.value = 0
    searched.value = false
    return
  }
  loading.value = true
  searched.value = true
  try {
    const data = await fetchLinkList({
      page: page.value,
      size: size.value,
      q: keyword.value || undefined,
      panType: filterPan.value || undefined,
      source: filterSource.value || undefined,
      invalid: filterInvalid.value === '' ? undefined : filterInvalid.value,
      mediaId: route.query.mediaId ? Number(route.query.mediaId) : undefined,
    })
    rows.value = data.records
    total.value = data.total
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '搜索失败')
  } finally {
    loading.value = false
  }
}

function onSearch() {
  if (!hasCriteria()) {
    ElMessage.warning('请输入关键词或选择筛选条件')
    return
  }
  page.value = 1
  loadList()
}

/** 2026-07-01T15:50:10 → 2026-07-01 15:50 */
function fmtTime(dt?: string) {
  return dt ? dt.replace('T', ' ').slice(0, 16) : '—'
}

function invalidLabel(v?: number) {
  return v === 1 ? '失效' : '有效'
}

function invalidTag(v?: number) {
  return v === 1 ? 'danger' : 'success'
}

function checkLabel(state?: string | null) {
  if (!state) return '未检'
  const map: Record<string, string> = {
    ok: '有效',
    bad: '失效',
    locked: '需码',
    uncertain: '未知',
    unsupported: '不支持',
  }
  return map[state] || state
}

onMounted(() => {
  if (route.query.mediaId || route.query.q) {
    if (route.query.q) keyword.value = String(route.query.q)
    onSearch()
  }
})
</script>

<template>
  <div class="page">
    <PageHeader
      title="链接管理"
      description="按 note / 片名 / URL 搜索网盘链接；需输入关键词或选择筛选条件"
    >
      <template #extra>
        <el-tag v-if="searched">共 {{ total.toLocaleString() }} 条</el-tag>
      </template>
    </PageHeader>

    <el-card shadow="never" class="block">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜 note / 片名 / URL"
          clearable
          style="width: 260px"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-select v-model="filterPan" placeholder="网盘" clearable style="width: 110px">
          <el-option label="夸克" value="quark" />
          <el-option label="百度" value="baidu" />
          <el-option label="阿里" value="aliyun" />
          <el-option label="迅雷" value="xunlei" />
          <el-option label="UC" value="uc" />
          <el-option label="磁力" value="magnet" />
        </el-select>
        <el-select v-model="filterSource" placeholder="来源" clearable style="width: 110px">
          <el-option label="manual" value="manual" />
          <el-option label="pansou" value="pansou" />
          <el-option label="crawl" value="crawl" />
        </el-select>
        <el-select v-model="filterInvalid" placeholder="状态" clearable style="width: 100px">
          <el-option label="有效" :value="0" />
          <el-option label="失效" :value="1" />
        </el-select>
        <el-button type="primary" @click="onSearch">搜索</el-button>
      </div>

      <el-empty
        v-if="!searched"
        description="输入关键词或选择筛选条件后搜索"
        :image-size="72"
      />

      <template v-else>
        <el-table v-loading="loading" :data="rows" stripe class="table">
          <el-table-column prop="mediaTitle" label="片名" min-width="120" show-overflow-tooltip />
          <el-table-column prop="panLabel" label="网盘" width="72" />
          <el-table-column prop="note" label="note" min-width="200" show-overflow-tooltip />
          <el-table-column prop="source" label="来源" width="80" />
          <el-table-column label="失效" width="72">
            <template #default="{ row }">
              <el-tag :type="invalidTag(row.invalid)" size="small">
                {{ invalidLabel(row.invalid) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="检测" width="72">
            <template #default="{ row }">
              <span class="check">{{ checkLabel(row.checkState) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="更新" width="140">
            <template #default="{ row }">{{ fmtTime(row.updatedAt) }}</template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="page"
            :page-size="size"
            :total="total"
            layout="total, prev, pager, next"
            @current-change="loadList"
          />
        </div>
      </template>
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
}

.table {
  width: 100%;
}

.check {
  font-size: 0.82rem;
  color: var(--text-soft);
}

.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
