<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/layout/PageHeader.vue'
import {
  JOB_STATUS_LABEL,
  JOB_STATUS_TYPE,
  JOB_TYPE_LABEL,
  PAN_LABEL,
  listJobs,
  type TransferJob,
} from '@/api/transfer'

const PAN_TYPES = ['quark', 'baidu', 'xunlei']
const JOB_STATUSES = ['pending', 'running', 'done', 'failed', 'canceled']

function fmt(dt?: string) {
  return dt ? dt.replace('T', ' ').slice(0, 19) : '—'
}

const jobs = ref<TransferJob[]>([])
const jobsLoading = ref(false)
const jobPage = ref(1)
const jobSize = ref(20)
const jobTotal = ref(0)
const jobFilterStatus = ref('')
const jobFilterPan = ref('')

async function loadJobs() {
  jobsLoading.value = true
  try {
    const data = await listJobs({
      page: jobPage.value,
      size: jobSize.value,
      status: jobFilterStatus.value || undefined,
      panType: jobFilterPan.value || undefined,
    })
    jobs.value = data.records
    jobTotal.value = data.total
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    jobsLoading.value = false
  }
}

function onJobSearch() {
  jobPage.value = 1
  loadJobs()
}

onMounted(() => {
  loadJobs()
})
</script>

<template>
  <div class="page">
    <PageHeader title="转存记录">
      <template #extra>
        <el-button :loading="jobsLoading" @click="loadJobs">刷新</el-button>
      </template>
    </PageHeader>

    <el-card shadow="never" class="block">
      <div class="toolbar">
        <el-select v-model="jobFilterStatus" placeholder="状态" clearable style="width: 120px" @change="onJobSearch">
          <el-option v-for="s in JOB_STATUSES" :key="s" :label="JOB_STATUS_LABEL[s]" :value="s" />
        </el-select>
        <el-select v-model="jobFilterPan" placeholder="网盘" clearable style="width: 110px" @change="onJobSearch">
          <el-option v-for="p in PAN_TYPES" :key="p" :label="PAN_LABEL[p]" :value="p" />
        </el-select>
        <el-button type="primary" @click="onJobSearch">搜索</el-button>
      </div>
      <el-table v-loading="jobsLoading" :data="jobs" stripe class="table">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="detail">
              <p><b>分享链接：</b>{{ row.shareUrl }}<span v-if="row.sharePwd"> （提取码 {{ row.sharePwd }}）</span></p>
              <p v-if="row.targetFolderId"><b>落地夹 id：</b>{{ row.targetFolderId }}</p>
              <p v-if="row.resultShareUrl"><b>我方分享链：</b>{{ row.resultShareUrl }}</p>
              <p v-if="row.workerId"><b>执行节点：</b>{{ row.workerId }}</p>
              <p v-if="row.errorMsg" class="err"><b>错误：</b>{{ row.errorMsg }}</p>
              <p v-if="row.resultJson"><b>结果快照：</b><code>{{ row.resultJson }}</code></p>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="ID" width="72" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ JOB_TYPE_LABEL[row.jobType] || row.jobType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="网盘" width="80">
          <template #default="{ row }">{{ PAN_LABEL[row.panType] || row.panType }}</template>
        </el-table-column>
        <el-table-column prop="shareUrl" label="分享链接" min-width="220" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="JOB_STATUS_TYPE[row.status]">{{ JOB_STATUS_LABEL[row.status] || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="尝试" width="72">
          <template #default="{ row }">{{ row.attempts }}/{{ row.maxAttempts }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ fmt(row.updatedAt) }}</template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="jobPage"
          :page-size="jobSize"
          :total="jobTotal"
          layout="total, prev, pager, next"
          @current-change="loadJobs"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.block {
  margin-top: 12px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.table {
  width: 100%;
}
.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
.detail {
  padding: 4px 12px;
  line-height: 1.9;
  font-size: 13px;
  color: #555;
  word-break: break-all;
}
.detail .err {
  color: var(--el-color-danger);
}
.detail code {
  background: #f4f4f5;
  padding: 2px 6px;
  border-radius: 4px;
}
</style>
