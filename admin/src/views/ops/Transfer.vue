<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/layout/PageHeader.vue'
import {
  COOKIE_PAN_TYPES,
  JOB_STATUS_LABEL,
  JOB_STATUS_TYPE,
  JOB_TYPE_LABEL,
  LOGIN_STATUS_LABEL,
  PAN_LABEL,
  addByCookie,
  deleteAccount,
  getBaiduAuthorizeUrl,
  getLoginStatus,
  listAccounts,
  listJobs,
  setAccountRole,
  setBaiduToken,
  startXunleiAuthorize,
  submitXunleiCode,
  type LoginSession,
  type TransferAccount,
  type TransferJob,
} from '@/api/transfer'

const PAN_TYPES = ['quark', 'baidu', 'xunlei']
const JOB_STATUSES = ['pending', 'running', 'done', 'failed', 'canceled']

const tab = ref('jobs')

function fmt(dt?: string) {
  return dt ? dt.replace('T', ' ').slice(0, 19) : '—'
}

// ---- 任务队列 ----
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

// ---- 网盘账号 ----
const accounts = ref<TransferAccount[]>([])
const accountsLoading = ref(false)

function fmtBytes(bytes?: number): string {
  if (bytes == null || bytes < 0) return '—'
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  const v = bytes / Math.pow(1024, i)
  return `${v.toFixed(v >= 100 || i === 0 ? 0 : 1)} ${units[i]}`
}

function spacePercent(row: TransferAccount): number {
  const total = row.totalSpace ?? 0
  const used = row.usedSpace ?? 0
  if (total <= 0 || used < 0) return 0
  return Math.min(100, Math.round((used / total) * 100))
}

async function loadAccounts() {
  accountsLoading.value = true
  try {
    accounts.value = await listAccounts()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    accountsLoading.value = false
  }
}

// 换号/加号后，账号昵称/容量由进程内 AccountInfoService 拉取（best-effort），
// 故成功后分批补刷，避免“换了 cookie 看着没生效、切个 tab 才更新”。
let accountRefreshTimers: number[] = []
function clearAccountRefreshes() {
  accountRefreshTimers.forEach((t) => window.clearTimeout(t))
  accountRefreshTimers = []
}
function scheduleAccountRefreshes() {
  clearAccountRefreshes()
  ;[3000, 8000, 15000, 25000, 35000].forEach((d) => {
    accountRefreshTimers.push(window.setTimeout(loadAccounts, d))
  })
}

/** 删除账号（通常因封号）：确认后直接删行 + 放弃其名下未删资源。 */
async function onDeleteAccount(row: TransferAccount) {
  try {
    await ElMessageBox.confirm(
      `确定删除账号「${row.accountName}」(${PAN_LABEL[row.panType] || row.panType})？\n` +
        '该账号名下「待清理」的转存资源将标记为放弃（账号已失效，无法再删除云端文件）。\n' +
        '删除后凭据即清除，账号池 30 秒内剔除该号。',
      '删除账号',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    const r = await deleteAccount(row.id)
    ElMessage.success(`已删除，放弃资源记录 ${r.abandoned} 条`)
    loadAccounts()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败')
  }
}

async function onSetRole(row: TransferAccount & { rolePending?: boolean }, role: string) {
  if ((row.role || 'transfer') === role) return
  row.rolePending = true
  try {
    await setAccountRole(row.id, role as 'transfer' | 'monitor')
    row.role = role
    ElMessage.success(role === 'monitor' ? '已设为监控转存号' : '已设为用户转存号')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '设置失败')
  } finally {
    row.rolePending = false
  }
}

// ---- 百度删除令牌（开放平台隐式授权）----
const baiduTokenDialog = ref(false)
const baiduTokenSaving = ref(false)
const baiduAuthorizeUrl = ref('')
const baiduTokenForm = reactive({ id: 0, accountName: '', token: '' })

async function openBaiduToken(row: TransferAccount) {
  baiduTokenForm.id = row.id
  baiduTokenForm.accountName = row.accountName
  baiduTokenForm.token = ''
  baiduTokenDialog.value = true
  try {
    const r = await getBaiduAuthorizeUrl()
    baiduAuthorizeUrl.value = r.authorizeUrl
  } catch (e) {
    baiduAuthorizeUrl.value = ''
    ElMessage.error(e instanceof Error ? e.message : '获取授权链接失败')
  }
}

async function copyBaiduUrl() {
  if (!baiduAuthorizeUrl.value) return
  try {
    await navigator.clipboard.writeText(baiduAuthorizeUrl.value)
    ElMessage.success('授权链接已复制，粘到新标签地址栏打开')
  } catch {
    ElMessage.warning('复制失败，请手动选中链接复制')
  }
}

async function submitBaiduToken() {
  if (!baiduTokenForm.token.trim()) {
    ElMessage.warning('请粘贴授权页返回的 access_token / 链接')
    return
  }
  baiduTokenSaving.value = true
  try {
    await setBaiduToken(baiduTokenForm.id, baiduTokenForm.token.trim())
    ElMessage.success('删除令牌已保存')
    baiduTokenDialog.value = false
    loadAccounts()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    baiduTokenSaving.value = false
  }
}

// ---- 加号 / 换号 ----
const loginDialog = ref(false)
const loginStarting = ref(false)
const loginForm = reactive({ panType: 'quark', accountName: '', cookie: '' })
const session = ref<LoginSession | null>(null)
const xunleiCode = ref('')
const codeSubmitting = ref(false)
let loginTimer: number | undefined

const isCookiePan = computed(() => COOKIE_PAN_TYPES.includes(loginForm.panType))
const dialogTitle = computed(() => (loginForm.accountName ? '换号 / 续凭据' : '添加网盘账号'))

/** 传 acct=对已有账号换凭据（覆盖同名）；不传=新增账号。 */
function openLogin(acct?: TransferAccount) {
  if (acct) {
    loginForm.panType = acct.panType
    loginForm.accountName = acct.accountName
  } else {
    loginForm.panType = 'quark'
    loginForm.accountName = ''
  }
  loginForm.cookie = ''
  session.value = null
  loginDialog.value = true
}

function stopLoginPoll() {
  if (loginTimer) {
    window.clearInterval(loginTimer)
    loginTimer = undefined
  }
}

async function pollLogin() {
  if (!session.value) return
  try {
    const s = await getLoginStatus(session.value.sessionId)
    session.value = s
    if (s.status === 'success') {
      stopLoginPoll()
      ElMessage.success(`落号成功：${s.accountName || ''}`)
      loadAccounts()
      scheduleAccountRefreshes()
    } else if (s.status === 'failed' || s.status === 'expired') {
      stopLoginPoll()
    }
  } catch (e) {
    void e
  }
}

function startPoll(s: LoginSession) {
  session.value = s
  loginTimer = window.setInterval(pollLogin, 2000)
  pollLogin()
}

/** 夸克/百度：提交粘贴的 cookie。 */
async function submitCookie() {
  if (!loginForm.cookie.trim()) {
    ElMessage.warning('请粘贴 cookie')
    return
  }
  loginStarting.value = true
  stopLoginPoll()
  try {
    const s = await addByCookie({
      panType: loginForm.panType,
      accountName: loginForm.accountName || undefined,
      cookie: loginForm.cookie.trim(),
    })
    startPoll(s)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '提交失败')
  } finally {
    loginStarting.value = false
  }
}

/** 迅雷：发起授权，打开授权页新标签，轮询等回调落号。 */
async function submitXunlei() {
  loginStarting.value = true
  stopLoginPoll()
  try {
    const r = await startXunleiAuthorize({
      accountName: loginForm.accountName || undefined,
    })
    window.open(r.authorizeUrl, '_blank', 'noopener')
    xunleiCode.value = ''
    startPoll({ sessionId: r.sessionId, panType: 'xunlei', status: 'pending_auth' })
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '发起失败')
  } finally {
    loginStarting.value = false
  }
}

/** 迅雷回调域名不通时的兜底：把授权后地址栏那串（含 code）贴回来换 token。code 仅约 120s 有效。 */
async function submitCode() {
  if (!session.value?.sessionId) {
    ElMessage.warning('请先发起授权')
    return
  }
  if (!xunleiCode.value.trim()) {
    ElMessage.warning('请粘贴授权后地址栏的 URL（含 code）')
    return
  }
  codeSubmitting.value = true
  try {
    const s = await submitXunleiCode({ sessionId: session.value.sessionId, code: xunleiCode.value.trim() })
    session.value = s
    ElMessage.success('已提交授权码，正在落号…')
    xunleiCode.value = ''
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '提交失败')
  } finally {
    codeSubmitting.value = false
  }
}

function submitLogin() {
  if (isCookiePan.value) {
    submitCookie()
  } else {
    submitXunlei()
  }
}

function resetLogin() {
  stopLoginPoll()
  session.value = null
  xunleiCode.value = ''
}

function closeLogin() {
  stopLoginPoll()
  loginDialog.value = false
}

function onTabChange(name: string | number) {
  if (name === 'jobs') loadJobs()
  else if (name === 'accounts') loadAccounts()
}

onMounted(() => {
  loadJobs()
})
onUnmounted(() => {
  stopLoginPoll()
  clearAccountRefreshes()
})
</script>

<template>
  <div class="page">
    <PageHeader
      title="转存"
      description="两种行为：用户转存（转存/缓存）用 transfer 号池；监控转存（创建/更新）用每盘唯一的 monitor 号。此处看任务与账号；监控资源在「每日更新」按剧管理。"
    >
    </PageHeader>

    <el-card shadow="never" class="block">
      <el-tabs v-model="tab" @tab-change="onTabChange">
        <!-- ============ 任务队列 ============ -->
        <el-tab-pane label="任务队列" name="jobs">
          <div class="toolbar">
            <el-select v-model="jobFilterStatus" placeholder="状态" clearable style="width: 120px" @change="onJobSearch">
              <el-option v-for="s in JOB_STATUSES" :key="s" :label="JOB_STATUS_LABEL[s]" :value="s" />
            </el-select>
            <el-select v-model="jobFilterPan" placeholder="网盘" clearable style="width: 110px" @change="onJobSearch">
              <el-option v-for="p in PAN_TYPES" :key="p" :label="PAN_LABEL[p]" :value="p" />
            </el-select>
            <el-button type="primary" @click="onJobSearch">搜索</el-button>
            <div class="tools-right">
              <el-button :loading="jobsLoading" @click="loadJobs">刷新</el-button>
            </div>
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
        </el-tab-pane>

        <!-- ============ 网盘账号 ============ -->
        <el-tab-pane label="网盘账号" name="accounts">
          <div class="toolbar">
            <el-tag type="info">共 {{ accounts.length }} 个账号</el-tag>
            <div class="tools-right">
              <el-button :loading="accountsLoading" @click="loadAccounts">刷新</el-button>
              <el-button type="primary" @click="openLogin()">添加账号</el-button>
            </div>
          </div>
          <el-table v-loading="accountsLoading" :data="accounts" stripe class="table">
            <el-table-column label="网盘" width="90">
              <template #default="{ row }">{{ PAN_LABEL[row.panType] || row.panType }}</template>
            </el-table-column>
            <el-table-column prop="accountName" label="账号名" min-width="140" />
            <el-table-column label="昵称" min-width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ row.nickname || '—' }}</template>
            </el-table-column>
            <el-table-column label="容量" min-width="170">
              <template #default="{ row }">
                <div v-if="row.totalSpace != null && row.totalSpace >= 0">
                  <el-progress
                    :percentage="spacePercent(row)"
                    :status="spacePercent(row) >= 90 ? 'exception' : undefined"
                    :stroke-width="6"
                    :show-text="false"
                  />
                  <span class="space-text">{{ fmtBytes(row.usedSpace) }} / {{ fmtBytes(row.totalSpace) }}</span>
                </div>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column label="分工" width="150">
              <template #default="{ row }">
                <el-select
                  :model-value="row.role || 'transfer'"
                  size="small"
                  :loading="row.rolePending"
                  @change="(v: string) => onSetRole(row, v)"
                >
                  <el-option label="用户转存" value="transfer" />
                  <el-option label="监控转存" value="monitor" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="启用" width="80">
              <template #default="{ row }">
                <el-tag size="small" :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="凭据" width="110">
              <template #default="{ row }">
                <el-tag v-if="!row.hasCredential" size="small" type="info">未登录</el-tag>
                <el-tag v-else size="small" :type="row.healthy ? 'success' : 'danger'">
                  {{ row.healthy ? '有效' : '失效·需重扫' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="删除令牌" width="120">
              <template #default="{ row }">
                <template v-if="row.panType === 'baidu'">
                  <el-tag v-if="!row.hasBaiduToken" size="small" type="info">未授权</el-tag>
                  <el-tag v-else-if="row.baiduTokenExpired" size="small" type="danger">已过期</el-tag>
                  <el-tag v-else size="small" type="success">
                    剩 {{ row.baiduTokenDaysLeft ?? '?' }} 天
                  </el-tag>
                </template>
                <span v-else class="hint">—</span>
              </template>
            </el-table-column>
            <el-table-column prop="note" label="备注" min-width="100" show-overflow-tooltip />
            <el-table-column label="最近可见" min-width="170">
              <template #default="{ row }">{{ fmt(row.lastSeenAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="280" fixed="right">
              <template #default="{ row }">
                <el-button
                  size="small"
                  :type="row.hasCredential && row.healthy ? 'default' : 'primary'"
                  :disabled="row.removing"
                  @click="openLogin(row)"
                >{{ !row.hasCredential ? '登录' : (COOKIE_PAN_TYPES.includes(row.panType) ? '换Cookie' : '重新授权') }}</el-button>
                <el-button
                  v-if="row.panType === 'baidu'"
                  size="small"
                  :type="row.hasBaiduToken && !row.baiduTokenExpired ? 'default' : 'warning'"
                  :disabled="row.removing"
                  @click="openBaiduToken(row)"
                >{{ row.hasBaiduToken ? '换删除令牌' : '授权删除' }}</el-button>
                <el-button
                  size="small"
                  type="danger"
                  :loading="row.removing"
                  @click="onDeleteAccount(row)"
                >{{ row.removing ? '移除中' : '删除' }}</el-button>
              </template>
            </el-table-column>
          </el-table>
          <p class="hint" style="margin-top: 12px">
            夸克/百度用粘贴 cookie 加号，迅雷走 OAuth 授权；凭据存主站库、账号在进程内执行。
            账号失效后点「换Cookie / 重新授权」覆盖同名号即可。
          </p>
          <p class="hint">
            百度「授权删除」：走开放平台官方接口删文件，避开网页删除天天要短信验证码。令牌约 30 天到期，
            过期后点「换删除令牌」重新授权一次即可（不影响转存用的 cookie）。
          </p>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 添加账号 / 换号 -->
    <el-dialog v-model="loginDialog" :title="dialogTitle" width="520" @closed="resetLogin">
      <p class="hint">
        夸克 / 百度：从浏览器复制整段 cookie 粘贴加号；迅雷：走 OAuth 授权（打开授权页登录目标账号并同意）。
        凭据存主站库，进程内执行器直接使用。
      </p>
      <el-alert
        v-if="loginForm.accountName"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
        :title="`换号「${loginForm.accountName}」：新凭据将覆盖同名账号`"
      />
      <el-form label-width="80px">
        <el-form-item label="网盘">
          <el-select v-model="loginForm.panType" style="width: 100%" :disabled="!!loginForm.accountName">
            <el-option v-for="p in PAN_TYPES" :key="p" :label="PAN_LABEL[p]" :value="p" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="isCookiePan" label="Cookie">
          <el-input
            v-model="loginForm.cookie"
            type="textarea"
            :rows="5"
            placeholder="粘贴从浏览器 DevTools / 插件复制的整段 cookie（形如 a=1; b=2; ...）"
          />
        </el-form-item>
        <el-form-item v-else label="说明">
          <span class="hint" style="margin: 0">点「发起授权」将打开迅雷授权页，请在新标签中登录<b>目标迅雷账号</b>并同意授权，回调后自动落号。</span>
        </el-form-item>
      </el-form>

      <!-- 状态区 -->
      <div v-if="session" class="statusbox">
        <el-tag
          size="small"
          :type="session.status === 'success' ? 'success' : session.status === 'failed' || session.status === 'expired' ? 'danger' : 'warning'"
        >
          {{ LOGIN_STATUS_LABEL[session.status] || session.status }}
        </el-tag>
        <span v-if="session.status === 'success'" class="ok-text">✅ 落号成功：{{ session.accountName }}</span>
        <span v-else-if="session.status === 'failed' || session.status === 'expired'" class="err-text">
          ✗ {{ session.message || LOGIN_STATUS_LABEL[session.status] }}
        </span>
        <span v-else class="wait-text">{{ LOGIN_STATUS_LABEL[session.status] }}…</span>
      </div>

      <!-- 迅雷回调域名不通时的兜底：手动回填授权码 -->
      <div v-if="!isCookiePan && session && session.status === 'pending_auth'" class="codebox">
        <el-divider content-position="left">回调打不开？手动回填授权码</el-divider>
        <p class="hint" style="margin: 0 0 8px">
          授权同意后浏览器会跳到回调地址（可能是打不开的 naspt.vip 页面）。把<b>地址栏那一整串 URL</b>（含
          <code>?code=...</code>）复制粘到下面，也可只贴 code。<b>迅雷 code 约 120 秒失效，请尽快提交。</b>
        </p>
        <el-input
          v-model="xunleiCode"
          type="textarea"
          :rows="3"
          placeholder="粘贴授权后地址栏完整 URL，形如 https://example.com/api/auto-resource/xunlei/callback?code=xxx&state=yyy"
        />
        <el-button
          type="primary"
          size="small"
          style="margin-top: 8px"
          :loading="codeSubmitting"
          @click="submitCode"
        >提交授权码换 token</el-button>
      </div>

      <template #footer>
        <el-button @click="closeLogin">关闭</el-button>
        <el-button
          v-if="session && (session.status === 'failed' || session.status === 'expired')"
          @click="resetLogin"
        >重新发起</el-button>
        <el-button
          v-else-if="session && session.status === 'success'"
          type="success"
          @click="closeLogin"
        >完成</el-button>
        <el-button
          v-else
          type="primary"
          :loading="loginStarting || (!!session && session.status !== 'success')"
          :disabled="!!session && session.status !== 'success'"
          @click="submitLogin"
        >{{ session ? '进行中…' : isCookiePan ? '提交 Cookie' : '发起授权' }}</el-button>
      </template>
    </el-dialog>

    <!-- 百度删除令牌：开放平台隐式授权，拿 access_token 专供走 xpan 官方接口删除 -->
    <el-dialog
      v-model="baiduTokenDialog"
      :title="`百度「${baiduTokenForm.accountName}」·授权删除令牌`"
      width="560px"
    >
      <p class="hint" style="margin: 0 0 10px">
        走百度开放平台官方接口删文件，避开网页删除天天要短信验证码。令牌约 30 天到期，过期后重来一次即可。
        <b>此令牌只管删除，不影响转存用的 Cookie。</b>
      </p>
      <ol class="hint" style="margin: 0 0 12px; padding-left: 18px; line-height: 1.9">
        <li>
          打开授权页 →
          <el-link
            v-if="baiduAuthorizeUrl"
            type="primary"
            :href="baiduAuthorizeUrl"
            target="_blank"
            rel="noopener noreferrer"
            referrerpolicy="no-referrer"
          >打开百度授权页</el-link>
          <el-button
            v-if="baiduAuthorizeUrl"
            link
            type="primary"
            size="small"
            @click="copyBaiduUrl"
          >复制链接</el-button>
          <span v-else>（正在获取授权链接…）</span>
        </li>
        <li>登录<b>本账号对应的百度号</b>并同意授权。</li>
        <li>授权成功后页面会显示一串 <code>access_token</code>（或跳到含 <code>#access_token=...</code> 的地址）。</li>
        <li>把<b>整条地址</b>或那串 token 复制，粘到下面保存。</li>
      </ol>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        style="margin: 0 0 12px"
        title="若报 referer_mismatch / Invalid Referer"
      >
        <span class="hint" style="margin: 0">
          别直接点链接（会带来源被百度拒）。点<b>「复制链接」</b>→ 新开一个浏览器标签，把链接<b>粘到地址栏回车</b>打开，
          地址栏访问不带 Referer 即可正常授权。
        </span>
      </el-alert>
      <el-input
        v-model="baiduTokenForm.token"
        type="textarea"
        :rows="4"
        placeholder="粘贴授权后地址栏完整 URL（含 #access_token=...&expires_in=...），或只贴 access_token"
      />
      <template #footer>
        <el-button @click="baiduTokenDialog = false">取消</el-button>
        <el-button type="primary" :loading="baiduTokenSaving" @click="submitBaiduToken">保存令牌</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page {
  padding: 0;
}
.block {
  margin-top: 12px;
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
.space-text {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: #909399;
  line-height: 1.6;
}
.mr4 {
  margin-right: 4px;
}
.statusbox {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  font-size: 13px;
}
.statusbox .ok-text {
  color: var(--el-color-success);
}
.statusbox .err-text {
  color: var(--el-color-danger);
  word-break: break-all;
}
.statusbox .wait-text {
  color: #909399;
}
</style>
