<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/layout/PageHeader.vue'
import { uploadAdminImage } from '@/api/config'
import {
  fetchLiveQrcodeConfig,
  fetchLiveQrcodeStats,
  updateLiveQrcodeConfig,
  type LiveQrcodeStats,
} from '@/api/liveQrcode'

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

onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <PageHeader
      title="活码 / 加群"
      description="上传群码与公众号图；站内弹窗与 /qr 活码页共用。换群只换图即可。"
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
</style>
