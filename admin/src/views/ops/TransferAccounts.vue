<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/layout/PageHeader.vue'
import {
  COOKIE_PAN_TYPES,
  LOGIN_STATUS_LABEL,
  PAN_LABEL,
  addByCookie,
  deleteAccount,
  getBaiduAuthorizeUrl,
  getLoginStatus,
  listAccounts,
  setAccountRole,
  setBaiduToken,
  startXunleiAuthorize,
  submitXunleiCode,
  type LoginSession,
  type TransferAccount,
} from '@/api/transfer'

const PAN_TYPES = ['quark', 'baidu', 'xunlei']

function fmt(dt?: string) {
  return dt ? dt.replace('T', ' ').slice(0, 19) : '—'
}

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

const loginDialog = ref(false)
const loginStarting = ref(false)
const loginForm = reactive({ panType: 'quark', accountName: '', cookie: '' })
const session = ref<LoginSession | null>(null)
const xunleiCode = ref('')
const codeSubmitting = ref(false)
let loginTimer: number | undefined

const isCookiePan = computed(() => COOKIE_PAN_TYPES.includes(loginForm.panType))
const dialogTitle = computed(() => (loginForm.accountName ? '换号 / 续凭据' : '添加网盘账号'))

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

onMounted(() => {
  loadAccounts()
})
onUnmounted(() => {
  stopLoginPoll()
  clearAccountRefreshes()
})
</script>

<template>
  <div class="page">
    <PageHeader title="网盘配置">
      <template #extra>
        <el-button :loading="accountsLoading" @click="loadAccounts">刷新</el-button>
        <el-button type="primary" @click="openLogin()">添加账号</el-button>
      </template>
    </PageHeader>

    <el-card shadow="never" class="block">
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
    </el-card>

    <el-dialog v-model="loginDialog" :title="dialogTitle" width="520" @closed="resetLogin">
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

      <div v-if="!isCookiePan && session && session.status === 'pending_auth'" class="codebox">
        <el-divider content-position="left">回调打不开？手动回填授权码</el-divider>
        <p class="hint" style="margin: 0 0 8px">
          授权同意后浏览器会跳到回调地址。把<b>地址栏那一整串 URL</b>（含
          <code>?code=...</code>）复制粘到下面，也可只贴 code。<b>迅雷 code 约 120 秒失效，请尽快提交。</b>
        </p>
        <el-input
          v-model="xunleiCode"
          type="textarea"
          :rows="3"
          placeholder="粘贴授权后地址栏完整 URL，或纯 code"
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

    <el-dialog
      v-model="baiduTokenDialog"
      :title="`百度「${baiduTokenForm.accountName}」·授权删除令牌`"
      width="560px"
    >
      <p class="hint" style="margin: 0 0 10px">
        走百度开放平台官方接口删文件。令牌约 30 天到期。
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
          别直接点链接。点<b>「复制链接」</b>→ 新开标签粘到地址栏回车打开。
        </span>
      </el-alert>
      <el-input
        v-model="baiduTokenForm.token"
        type="textarea"
        :rows="4"
        placeholder="粘贴授权后地址栏完整 URL（含 #access_token=...），或只贴 access_token"
      />
      <template #footer>
        <el-button @click="baiduTokenDialog = false">取消</el-button>
        <el-button type="primary" :loading="baiduTokenSaving" @click="submitBaiduToken">保存令牌</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.block {
  margin-top: 12px;
}
.table {
  width: 100%;
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
