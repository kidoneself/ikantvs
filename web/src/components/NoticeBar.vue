<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useSiteStore } from '@/store/site'
import NoticeDialog from '@/components/NoticeDialog.vue'

const emit = defineEmits<{ 'open-contact': [] }>()

const STORAGE_KEY = 'jy:notice:dismissed'
const AUTO_KEY = 'jy:notice:auto-popped'

const site = useSiteStore()
const { noticeEnabled, noticeTitle, noticeContent, noticeShowOnce, loaded } = storeToRefs(site)

const enabled = computed(() => noticeEnabled.value)
const showOnce = computed(() => noticeShowOnce.value)
const title = computed(() => (noticeTitle.value || '').trim() || '网站公告')
const content = computed(() => (noticeContent.value || '').trim())
const hasContent = computed(() => !!content.value)

function htmlToText(html: string): string {
  if (!html) return ''
  return html
    .replace(/<br\s*\/?>/gi, ' ')
    .replace(/<\/(p|div|li|h\d)>/gi, ' ')
    .replace(/<[^>]+>/g, '')
    .replace(/&nbsp;/g, ' ')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/\s+/g, ' ')
    .trim()
}

const barText = computed(() => {
  const t = title.value
  const c = htmlToText(content.value)
  if (!c) return t
  if (!t || t === '网站公告') return c
  return `${t}：${c}`
})

const visible = ref(false)
const dialogOpen = ref(false)
/** 本次页面生命周期内已自动弹过的 content hash，避免 watch 重复弹。 */
const autoPoppedSession = ref('')

function hash(s: string) {
  let h = 0
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0
  return String(h)
}

function contentKey() {
  return hash(title.value + '|' + content.value)
}

function check() {
  if (!loaded.value || !enabled.value || (!title.value && !content.value)) {
    visible.value = false
    return
  }
  if (!showOnce.value) {
    visible.value = true
    return
  }
  try {
    visible.value = localStorage.getItem(STORAGE_KEY) !== contentKey()
  } catch {
    visible.value = true
  }
}

function dismiss() {
  visible.value = false
  dialogOpen.value = false
  if (showOnce.value) {
    try {
      localStorage.setItem(STORAGE_KEY, contentKey())
    } catch {
      /* ignore */
    }
  }
}

function openDialog() {
  if (!hasContent.value) return
  dialogOpen.value = true
}

function markAutoPopped() {
  try {
    localStorage.setItem(AUTO_KEY, contentKey())
  } catch {
    /* ignore */
  }
}

function hasAutoPopped(): boolean {
  try {
    return localStorage.getItem(AUTO_KEY) === contentKey()
  } catch {
    return false
  }
}

function onOpenContact() {
  emit('open-contact')
}

watch(dialogOpen, (open, prev) => {
  if (prev && !open && showOnce.value) {
    markAutoPopped()
  }
})

function tryAutoPop() {
  const key = contentKey()
  if (!loaded.value || !visible.value || !hasContent.value) return
  if (autoPoppedSession.value === key) return
  if (showOnce.value && hasAutoPopped()) return
  autoPoppedSession.value = key
  dialogOpen.value = true
}

watch([title, content, enabled, showOnce, loaded], () => {
  check()
  tryAutoPop()
})

onMounted(() => {
  check()
  tryAutoPop()
})
</script>

<template>
  <Transition name="notice-slide">
    <div
      v-if="visible"
      class="notice-bar"
      :class="{ 'is-clickable': hasContent }"
      role="button"
      tabindex="0"
      @click="openDialog"
      @keyup.enter="openDialog"
    >
      <div class="notice-inner container">
        <span class="notice-icon" aria-hidden="true">📢</span>
        <span class="notice-text" :title="barText">{{ barText }}</span>
        <span v-if="hasContent" class="notice-more">查看详情</span>
        <button class="notice-close" type="button" aria-label="关闭公告" @click.stop="dismiss">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
            <path d="M18 6L6 18M6 6l12 12" />
          </svg>
        </button>
      </div>
    </div>
  </Transition>

  <NoticeDialog
    v-model="dialogOpen"
    :title="title"
    :content="content"
    @open-contact="onOpenContact"
  />
</template>

<style scoped lang="scss">
.notice-bar {
  background: linear-gradient(90deg, color-mix(in srgb, var(--brand) 14%, transparent), color-mix(in srgb, var(--brand) 4%, transparent));
  border-bottom: 1px solid var(--border);
  font-size: 13px;
  color: var(--text);
}

.notice-bar.is-clickable {
  cursor: pointer;

  &:hover {
    background: linear-gradient(90deg, color-mix(in srgb, var(--brand) 20%, transparent), color-mix(in srgb, var(--brand) 6%, transparent));
  }
}

.notice-inner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-top: 8px;
  padding-bottom: 8px;
}

.notice-icon {
  flex-shrink: 0;
  font-size: 14px;
  line-height: 1;
}

.notice-text {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.notice-more {
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--brand-strong);
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--brand-soft);
}

.notice-close {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  border-radius: 999px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;

  &:hover {
    background: var(--surface-2);
    color: var(--text);
  }
}

.notice-slide-enter-active,
.notice-slide-leave-active {
  transition:
    max-height 0.25s ease,
    opacity 0.2s ease;
  overflow: hidden;
}

.notice-slide-enter-from,
.notice-slide-leave-to {
  max-height: 0;
  opacity: 0;
}

.notice-slide-enter-to,
.notice-slide-leave-from {
  max-height: 60px;
  opacity: 1;
}
</style>
