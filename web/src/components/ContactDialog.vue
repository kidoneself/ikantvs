<script setup lang="ts">
import { computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useSiteStore } from '@/store/site'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const site = useSiteStore()
const { contactTitle, contactTip, contactGroupQrcode, contactMpQrcode } = storeToRefs(site)

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})

const hasGroup = computed(() => !!contactGroupQrcode.value)
const hasMp = computed(() => !!contactMpQrcode.value)
const hasAny = computed(() => hasGroup.value || hasMp.value)

function close() {
  visible.value = false
}
</script>

<template>
  <Teleport to="body">
    <Transition name="cd-fade">
      <div v-if="visible" class="cd-overlay" @click.self="close">
        <div class="cd-dialog" role="dialog" aria-modal="true">
          <header class="cd-header">
            <h3>{{ contactTitle || '防止失联' }}</h3>
            <button class="cd-close" type="button" aria-label="关闭" @click="close">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M18 6L6 18M6 6l12 12" />
              </svg>
            </button>
          </header>

          <div class="cd-body">
            <template v-if="hasAny">
              <div class="cd-cards" :class="{ dual: hasGroup && hasMp }">
                <div v-if="hasGroup" class="cd-card">
                  <span class="badge">微信群</span>
                  <img :src="contactGroupQrcode" alt="微信群二维码" />
                  <p class="label">资源互助群</p>
                </div>
                <div v-if="hasMp" class="cd-card">
                  <span class="badge mp">公众号</span>
                  <img :src="contactMpQrcode" alt="公众号二维码" />
                  <p class="label">官方公众号</p>
                </div>
              </div>
              <p class="cd-tip">{{ contactTip || '长按识别或截图后微信扫一扫' }}</p>
            </template>
            <template v-else>
              <p class="cd-empty">暂未配置联系二维码</p>
            </template>
          </div>

          <footer class="cd-footer">
            <button class="cd-btn" type="button" @click="close">我知道了</button>
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped lang="scss">
.cd-overlay {
  position: fixed;
  inset: 0;
  z-index: 2100;
  background: rgba(15, 23, 42, 0.55);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
}

.cd-dialog {
  width: 100%;
  max-width: 420px;
  background: var(--surface);
  border-radius: var(--radius-l);
  overflow: hidden;
  box-shadow: var(--shadow-hover);
}

.cd-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid var(--border);

  h3 {
    margin: 0;
    font-size: 17px;
    font-weight: 600;
  }
}

.cd-close {
  border: none;
  background: transparent;
  padding: 4px;
  border-radius: 6px;
  color: var(--text-soft);
  cursor: pointer;

  &:hover {
    background: var(--surface-2);
    color: var(--text);
  }
}

.cd-body {
  padding: 18px;
}

.cd-cards {
  display: flex;
  justify-content: center;
  gap: 14px;

  &.dual .cd-card {
    flex: 1;
    max-width: 170px;
  }
}

.cd-card {
  text-align: center;

  .badge {
    display: inline-block;
    font-size: 11px;
    font-weight: 600;
    padding: 2px 8px;
    border-radius: 999px;
    background: var(--brand-soft);
    color: var(--brand-strong);
    margin-bottom: 8px;

    &.mp {
      background: #eef2ff;
      color: #4f46e5;
    }
  }

  img {
    width: 100%;
    max-width: 160px;
    aspect-ratio: 1;
    object-fit: contain;
    border-radius: 10px;
    border: 1px solid var(--border);
    background: #fff;
  }

  .label {
    margin: 8px 0 0;
    font-size: 13px;
    color: var(--text-soft);
  }
}

.cd-tip {
  margin: 14px 0 0;
  text-align: center;
  font-size: 13px;
  color: var(--text-muted);
}

.cd-empty {
  text-align: center;
  color: var(--text-muted);
  padding: 20px 0;
}

.cd-footer {
  padding: 0 18px 18px;
  display: flex;
  justify-content: center;
}

.cd-btn {
  border: none;
  background: var(--brand);
  color: #fff;
  padding: 8px 28px;
  border-radius: 999px;
  font-weight: 600;
  cursor: pointer;

  &:hover {
    background: var(--brand-strong);
  }
}

.cd-fade-enter-active,
.cd-fade-leave-active {
  transition: opacity 0.18s ease;
}
.cd-fade-enter-from,
.cd-fade-leave-to {
  opacity: 0;
}
</style>
