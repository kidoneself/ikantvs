<script setup lang="ts">
import { computed, ref } from 'vue'

const props = defineProps<{
  title: string
  /** 详情/大图用 */
  poster?: string
  /** 列表用小图，优先于 poster */
  thumb?: string
  category?: string
}>()

const ICONS: Record<string, string> = {
  电影: '🎬',
  剧集: '📺',
  动漫: '🌸',
  综艺: '🎤',
}

const failed = ref(false)
const src = computed(() => props.thumb || props.poster)
const showImg = computed(() => !!src.value && !failed.value)
const icon = computed(() => (props.category ? ICONS[props.category] || '🎞️' : '🎞️'))

/** 用标题做个稳定 hash → 色相，保证同一资源封面颜色固定且各不相同 */
const hue = computed(() => {
  let h = 0
  for (const ch of props.title) h = (h * 31 + ch.charCodeAt(0)) % 360
  return h
})
const gradient = computed(
  () =>
    `linear-gradient(150deg, hsl(${hue.value} 52% 42%), hsl(${(hue.value + 40) % 360} 48% 30%))`,
)
</script>

<template>
  <div class="cover">
    <img v-if="showImg" :src="src" :alt="title" loading="lazy" @error="failed = true" />
    <div v-else class="placeholder" :style="{ background: gradient }">
      <span class="watermark">{{ icon }}</span>
      <span class="name">{{ title }}</span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.cover {
  width: 100%;
  height: 100%;
}
img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.placeholder {
  position: relative;
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px;
  overflow: hidden;

  .watermark {
    position: absolute;
    right: -8px;
    bottom: -14px;
    font-size: 88px;
    opacity: 0.16;
    filter: grayscale(1);
  }
  .name {
    position: relative;
    color: #fff;
    font-size: 16px;
    font-weight: 700;
    line-height: 1.4;
    text-align: center;
    text-shadow: 0 1px 8px rgba(0, 0, 0, 0.35);
    display: -webkit-box;
    -webkit-line-clamp: 3;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
}
</style>
