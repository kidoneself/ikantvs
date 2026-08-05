<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    title?: string
    content?: string
  }>(),
  {
    title: '',
    content: '',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  close: []
  'open-contact': []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})

function close() {
  visible.value = false
  emit('close')
}

/** 点空白关闭；链接正常跳转；进群按钮上抛 open-contact（加群功能后续接）。 */
function onAnyClick(e: MouseEvent) {
  const target = e.target as HTMLElement | null
  const groupBtn = target?.closest(
    'a[data-action="open-contact"], a[data-notice-group], button.notice-open-group',
  ) as HTMLElement | null
  if (groupBtn) {
    e.preventDefault()
    e.stopPropagation()
    visible.value = false
    emit('open-contact')
    return
  }

  const anchor = target?.closest('a') as HTMLAnchorElement | null
  if (anchor?.href) {
    if (!anchor.target) anchor.target = '_blank'
    if (!anchor.rel) anchor.rel = 'noopener noreferrer'
    close()
    return
  }
  close()
}
</script>

<template>
  <Teleport to="body">
    <Transition name="nd-fade">
      <div
        v-if="visible"
        class="nd-overlay"
        role="dialog"
        aria-modal="true"
        aria-labelledby="nd-title"
        @click="onAnyClick"
      >
        <div class="nd-dialog">
          <header class="nd-header">
            <span class="nd-icon" aria-hidden="true">📢</span>
            <h3 id="nd-title">{{ title || '网站公告' }}</h3>
            <button class="nd-close" type="button" aria-label="关闭" @click.stop="close">
              <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M18 6L6 18M6 6l12 12" />
              </svg>
            </button>
          </header>

          <div class="nd-body">
            <div v-if="content" class="nd-content" v-html="content" />
            <div v-else class="nd-empty">暂无公告内容</div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped lang="scss">
.nd-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  background: rgba(15, 23, 42, 0.55);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  cursor: pointer;
}

.nd-dialog {
  width: 100%;
  max-width: 520px;
  max-height: calc(100vh - 32px);
  background: var(--surface);
  border-radius: var(--radius-l);
  overflow: hidden;
  box-shadow: var(--shadow-hover);
  display: flex;
  flex-direction: column;
  cursor: pointer;
}

.nd-header {
  padding: 16px 20px;
  display: flex;
  align-items: center;
  gap: 8px;
  border-bottom: 1px solid var(--border);
}

.nd-icon {
  font-size: 18px;
  line-height: 1;
}

.nd-header h3 {
  margin: 0;
  flex: 1;
  font-size: 17px;
  font-weight: 600;
  color: var(--text);
}

.nd-close {
  border: none;
  background: transparent;
  padding: 4px;
  border-radius: var(--radius-s);
  color: var(--text-soft);
  cursor: pointer;

  &:hover {
    background: var(--surface-2);
    color: var(--text);
  }
}

.nd-body {
  padding: 20px;
  overflow-y: auto;
  color: var(--text);
  font-size: 14px;
  line-height: 1.7;
}

.nd-empty {
  color: var(--text-muted);
  text-align: center;
  padding: 16px 0;
}

.nd-content :deep(p) {
  margin: 0 0 8px;
}

.nd-content :deep(p:last-child) {
  margin-bottom: 0;
}

.nd-content :deep(a) {
  color: var(--brand);
  text-decoration: underline;
  word-break: break-all;
  cursor: pointer;
}

.nd-content :deep(ul),
.nd-content :deep(ol) {
  padding-left: 1.4em;
  margin: 0 0 8px;
}

.nd-content :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: var(--radius-s);
  margin: 8px 0;
}

.nd-content :deep(strong) {
  font-weight: 600;
}

.nd-content :deep(code) {
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--surface-2);
  font-size: 0.9em;
}

.nd-fade-enter-active,
.nd-fade-leave-active {
  transition: opacity 0.18s ease;
}
.nd-fade-enter-from,
.nd-fade-leave-to {
  opacity: 0;
}
</style>
