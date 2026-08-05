<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import QRCode from 'qrcode'
import PageHeader from '@/components/layout/PageHeader.vue'
import { uploadAdminImage } from '@/api/config'
import {
  fetchLiveQrcodeConfig,
  fetchLiveQrcodeStats,
  updateLiveQrcodeConfig,
  type LiveQrcodeStats,
} from '@/api/liveQrcode'

/** 推广活码根 URL；部署后改为你的前台域名 + /qr */
const PROMO_BASE = 'https://example.com/qr'

const loading = ref(false)
const saving = ref(false)
const uploadingGroup = ref(false)
const uploadingMp = ref(false)

const form = ref({
  qrcodeImage: '',
  mpQrcodeImage: '',
  title: '防止失联',
  tipText: '长按识别二维码，加入交流群',
  status: 1,
  scanCount: 0,
})

const stats = ref<LiveQrcodeStats>({
  totalCount: 0,
  todayCount: 0,
  sourceStats: [],
  trendStats: [],
})

const customSource = ref('')
const customChannels = ref<string[]>([])
const canvasRefs = ref<Record<string, HTMLCanvasElement>>({})

const defaultChannels = [
  { name: 'default', label: '默认', from: '' },
  { name: 'wangpan', label: '网盘', from: 'wangpan' },
  { name: 'xiaohongshu', label: '小红书', from: 'xiaohongshu' },
  { name: 'website', label: '网站', from: 'website' },
]

const qrcodeList = computed(() => {
  const list = defaultChannels.map((ch) => ({
    ...ch,
    url: ch.from ? `${PROMO_BASE}?from=${ch.from}` : PROMO_BASE,
  }))
  for (const ch of customChannels.value) {
    list.push({ name: ch, label: ch, from: ch, url: `${PROMO_BASE}?from=${ch}` })
  }
  return list
})

function setCanvasRef(el: unknown, name: string) {
  if (el instanceof HTMLCanvasElement) canvasRefs.value[name] = el
}

async function generateAllQrcodes() {
  await nextTick()
  for (const item of qrcodeList.value) {
    const canvas = canvasRefs.value[item.name]
    if (!canvas) continue
    try {
      await QRCode.toCanvas(canvas, item.url, {
        width: 140,
        margin: 2,
        color: { dark: '#000000', light: '#FFFFFF' },
      })
    } catch {
      /* ignore */
    }
  }
}

watch(qrcodeList, () => void generateAllQrcodes(), { deep: true })

async function load() {
  loading.value = true
  try {
    const [cfg, st] = await Promise.all([fetchLiveQrcodeConfig(), fetchLiveQrcodeStats()])
    form.value = {
      qrcodeImage: cfg.qrcodeImage || '',
      mpQrcodeImage: cfg.mpQrcodeImage || '',
      title: cfg.title || '防止失联',
      tipText: cfg.tipText || '',
      status: cfg.status ?? 1,
      scanCount: cfg.scanCount || 0,
    }
    stats.value = st
    await generateAllQrcodes()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    const cfg = await updateLiveQrcodeConfig({
      qrcodeImage: form.value.qrcodeImage,
      mpQrcodeImage: form.value.mpQrcodeImage,
      title: form.value.title,
      tipText: form.value.tipText,
      status: form.value.status,
    })
    form.value.scanCount = cfg.scanCount
    ElMessage.success('已保存，站内加群与活码页立即生效')
    stats.value = await fetchLiveQrcodeStats()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function uploadGroup(file: File) {
  uploadingGroup.value = true
  try {
    form.value.qrcodeImage = await uploadAdminImage(file, 'contact')
    ElMessage.success('群码已上传，记得保存')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '上传失败')
  } finally {
    uploadingGroup.value = false
  }
  return false
}

async function uploadMp(file: File) {
  uploadingMp.value = true
  try {
    form.value.mpQrcodeImage = await uploadAdminImage(file, 'contact')
    ElMessage.success('公众号码已上传，记得保存')
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '上传失败')
  } finally {
    uploadingMp.value = false
  }
  return false
}

function copyLink(url: string) {
  navigator.clipboard.writeText(url).then(
    () => ElMessage.success('已复制'),
    () => ElMessage.error('复制失败'),
  )
}

function downloadQrcode(name: string, label: string) {
  const canvas = canvasRefs.value[name]
  if (!canvas) return
  const a = document.createElement('a')
  a.download = `活码-${label}.png`
  a.href = canvas.toDataURL('image/png')
  a.click()
}

function addCustomChannel() {
  const name = customSource.value.trim().toLowerCase()
  if (!name) return
  if (customChannels.value.includes(name) || defaultChannels.some((c) => c.from === name)) {
    ElMessage.warning('该渠道已存在')
    return
  }
  customChannels.value.push(name)
  customSource.value = ''
}

onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <PageHeader
      title="活码 / 加群"
      description="群码与公众号可分别上传；站内弹窗与 /qr 活码页共用。换群只换图，已发出去的投放码不用重打。"
    />

    <el-row :gutter="16">
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="block">
          <template #header>联系二维码</template>
          <el-form label-width="100px">
            <el-form-item label="微信群码">
              <div class="upload-box">
                <el-upload
                  :show-file-list="false"
                  accept="image/*"
                  :disabled="uploadingGroup"
                  :before-upload="uploadGroup"
                >
                  <div v-if="form.qrcodeImage" class="preview">
                    <img :src="form.qrcodeImage" alt="群码" />
                    <span>点击更换</span>
                  </div>
                  <el-button v-else :loading="uploadingGroup">上传群二维码</el-button>
                </el-upload>
              </div>
            </el-form-item>
            <el-form-item label="公众号码">
              <div class="upload-box">
                <el-upload
                  :show-file-list="false"
                  accept="image/*"
                  :disabled="uploadingMp"
                  :before-upload="uploadMp"
                >
                  <div v-if="form.mpQrcodeImage" class="preview">
                    <img :src="form.mpQrcodeImage" alt="公众号" />
                    <span>点击更换</span>
                  </div>
                  <el-button v-else :loading="uploadingMp">上传公众号二维码</el-button>
                </el-upload>
              </div>
            </el-form-item>
            <el-form-item label="标题">
              <el-input v-model="form.title" maxlength="50" show-word-limit />
            </el-form-item>
            <el-form-item label="引导文案">
              <el-input v-model="form.tipText" type="textarea" :rows="2" maxlength="200" />
            </el-form-item>
            <el-form-item label="启用">
              <el-switch
                :model-value="form.status === 1"
                active-text="开"
                inactive-text="关"
                @update:model-value="(v: boolean) => (form.status = v ? 1 : 0)"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="save">保存</el-button>
              <a class="preview-link" href="/qr" target="_blank" rel="noopener">预览活码页</a>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="block">
          <template #header>访问统计</template>
          <div class="stat-row">
            <div class="stat">
              <div class="n">{{ stats.totalCount }}</div>
              <div class="l">累计访问</div>
            </div>
            <div class="stat">
              <div class="n">{{ stats.todayCount }}</div>
              <div class="l">今日</div>
            </div>
          </div>
          <el-table :data="stats.sourceStats || []" size="small" empty-text="暂无来源数据">
            <el-table-column prop="source" label="来源" />
            <el-table-column prop="count" label="次数" width="90" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="block" style="margin-top: 16px">
      <template #header>
        <div class="card-head">
          <span>推广活码（部署后改为你的域名/qr）</span>
          <div class="custom">
            <el-input v-model="customSource" placeholder="自定义渠道" style="width: 160px" @keyup.enter="addCustomChannel" />
            <el-button :disabled="!customSource.trim()" @click="addCustomChannel">生成</el-button>
          </div>
        </div>
      </template>
      <div class="promo-grid">
        <div v-for="item in qrcodeList" :key="item.name" class="promo-item">
          <div class="name">{{ item.label }}</div>
          <canvas :ref="(el) => setCanvasRef(el, item.name)" />
          <div class="ops">
            <el-button size="small" @click="copyLink(item.url)">复制链接</el-button>
            <el-button size="small" type="primary" @click="downloadQrcode(item.name, item.label)">下载</el-button>
          </div>
          <code class="url">{{ item.url }}</code>
        </div>
      </div>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.block {
  border: 1px solid var(--border);
}

.preview-link {
  margin-left: 12px;
  color: var(--primary, #3b82f6);
  font-size: 0.9rem;
}

.upload-box .preview {
  width: 140px;
  height: 140px;
  border: 1px dashed var(--border);
  border-radius: 8px;
  position: relative;
  overflow: hidden;
  cursor: pointer;

  img {
    width: 100%;
    height: 100%;
    object-fit: contain;
    background: #fff;
  }

  span {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    background: rgba(0, 0, 0, 0.45);
    color: #fff;
    font-size: 12px;
    opacity: 0;
  }

  &:hover span {
    opacity: 1;
  }
}

.stat-row {
  display: flex;
  gap: 24px;
  margin-bottom: 16px;
}

.stat {
  .n {
    font-size: 1.6rem;
    font-weight: 700;
  }
  .l {
    color: var(--text-soft);
    font-size: 0.85rem;
  }
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.custom {
  display: flex;
  gap: 8px;
}

.promo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 16px;
}

.promo-item {
  text-align: center;
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: 10px;

  .name {
    font-weight: 600;
    margin-bottom: 8px;
  }

  .ops {
    display: flex;
    justify-content: center;
    gap: 6px;
    margin: 8px 0;
  }

  .url {
    display: block;
    font-size: 0.7rem;
    color: var(--text-muted);
    word-break: break-all;
  }
}
</style>
