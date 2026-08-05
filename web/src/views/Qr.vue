<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { fetchLiveQrPage, type LiveQrPage } from '@/api/site'

const route = useRoute()
const loading = ref(true)
const error = ref('')
const data = ref<LiveQrPage | null>(null)

onMounted(async () => {
  try {
    const from = typeof route.query.from === 'string' ? route.query.from : undefined
    data.value = await fetchLiveQrPage(from)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="qr-page">
    <div v-if="loading" class="state">加载中…</div>
    <div v-else-if="error" class="state error">
      <p>{{ error }}</p>
    </div>
    <div v-else-if="data" class="content">
      <h1>{{ data.title || '防止失联' }}</h1>
      <p class="sub">选择你喜欢的方式，和我们保持联系</p>

      <div class="cards">
        <div v-if="data.qrcodeImage" class="card">
          <span class="badge">微信群</span>
          <img :src="data.qrcodeImage" alt="微信群二维码" />
          <p class="name">资源互助群</p>
          <p class="desc">失效补链 · 资源求助</p>
        </div>
        <div v-if="data.mpQrcodeImage" class="card">
          <span class="badge mp">公众号</span>
          <img :src="data.mpQrcodeImage" alt="公众号二维码" />
          <p class="name">官方公众号</p>
          <p class="desc">更新推送 · 关注不迷路</p>
        </div>
        <div v-if="!data.qrcodeImage && !data.mpQrcodeImage" class="card empty">
          <p>暂未配置二维码</p>
        </div>
      </div>

      <p class="tip">{{ data.tipText || '长按识别二维码 或 截图后微信扫一扫' }}</p>
      <p v-if="data.scanCount" class="count">已有 <strong>{{ data.scanCount }}</strong> 人访问</p>
    </div>
  </div>
</template>

<style scoped lang="scss">
.qr-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #e8f8f1 0%, var(--bg) 40%);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 40px 16px 48px;
}

.content {
  width: 100%;
  max-width: 520px;
  text-align: center;

  h1 {
    margin: 0;
    font-size: 1.6rem;
    font-weight: 800;
    color: var(--text);
  }

  .sub {
    margin: 8px 0 24px;
    color: var(--text-soft);
  }
}

.cards {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 16px;
}

.card {
  flex: 1 1 200px;
  max-width: 240px;
  background: var(--surface);
  border-radius: var(--radius-l);
  padding: 18px 14px;
  box-shadow: var(--shadow);
  border: 1px solid var(--border);

  .badge {
    display: inline-block;
    font-size: 12px;
    font-weight: 700;
    padding: 2px 10px;
    border-radius: 999px;
    background: var(--brand-soft);
    color: var(--brand-strong);
    margin-bottom: 12px;

    &.mp {
      background: #eef2ff;
      color: #4f46e5;
    }
  }

  img {
    width: 100%;
    max-width: 180px;
    aspect-ratio: 1;
    object-fit: contain;
    border-radius: 10px;
    background: #fff;
  }

  .name {
    margin: 12px 0 4px;
    font-weight: 700;
  }

  .desc {
    margin: 0;
    font-size: 13px;
    color: var(--text-muted);
  }

  &.empty {
    padding: 40px 16px;
    color: var(--text-muted);
  }
}

.tip {
  margin: 24px 0 8px;
  color: var(--text-soft);
  font-size: 14px;
}

.count {
  margin: 0;
  font-size: 13px;
  color: var(--text-muted);

  strong {
    color: var(--brand-strong);
  }
}

.state {
  margin-top: 20vh;
  text-align: center;
  color: var(--text-soft);

  &.error {
    color: #dc2626;
  }
}
</style>
