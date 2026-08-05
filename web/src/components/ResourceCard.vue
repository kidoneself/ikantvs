<script setup lang="ts">
import { useRouter } from 'vue-router'
import CoverImage from '@/components/CoverImage.vue'
import { track } from '@/api/track'
import type { ResourceItem } from '@/api/content'
import { findQuery } from '@/lib/findSearch'

const props = defineProps<{ item: ResourceItem; source?: string; rank?: number }>()
const router = useRouter()

function go() {
  if (props.source) track('card_click', { mediaId: props.item.id, tag: props.source, num: props.rank })
  // 老站逻辑：点卡片 = 用片名搜资源，不是进详情
  router.push(findQuery(props.item.title))
}
</script>

<template>
  <article class="card" @click="go">
    <div class="poster">
      <CoverImage
        :title="item.title"
        :poster="item.poster"
        :thumb="item.posterThumb"
        :category="item.category"
      />
      <span class="cat">{{ item.category }}</span>
      <span class="rating" v-if="item.rating">{{ item.rating.toFixed(1) }}</span>
    </div>
    <div class="meta">
      <h3 class="title">{{ item.title }}</h3>
      <p class="sub">
        {{ item.year }} · {{ item.region }}<template v-if="item.airProgress"> · {{ item.airProgress }}</template><template v-else-if="item.genres[0]"> · {{ item.genres[0] }}</template>
      </p>
      <div class="tags">
        <span v-for="t in item.genres.slice(0, 3)" :key="t" class="tag">{{ t }}</span>
      </div>
    </div>
  </article>
</template>

<style scoped lang="scss">
.card {
  background: var(--surface);
  border-radius: var(--radius);
  overflow: hidden;
  cursor: pointer;
  box-shadow: var(--shadow-s);
  border: 1px solid var(--border);
  transition: transform 0.18s, box-shadow 0.18s;

  &:hover {
    transform: translateY(-4px);
    box-shadow: var(--shadow-hover);
    .poster :deep(img) {
      transform: scale(1.06);
    }
  }
}

.poster {
  position: relative;
  aspect-ratio: 5 / 6;
  overflow: hidden;
  background: var(--surface-2);

  :deep(img) {
    transition: transform 0.4s ease;
  }

  .rating {
    position: absolute;
    bottom: 8px;
    right: 8px;
    background: rgba(0, 0, 0, 0.62);
    color: var(--rating);
    font-weight: 700;
    font-size: 12px;
    padding: 2px 7px;
    border-radius: 6px;
    backdrop-filter: blur(4px);
  }
  .cat {
    position: absolute;
    top: 8px;
    left: 8px;
    background: rgba(22, 184, 125, 0.92);
    color: #fff;
    font-size: 11px;
    padding: 2px 7px;
    border-radius: 6px;
  }
}

.meta {
  padding: 10px 12px 12px;

  .title {
    margin: 0;
    font-size: 14px;
    font-weight: 600;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .sub {
    margin: 4px 0 8px;
    font-size: 12px;
    color: var(--text-muted);
  }
  .tags {
    display: flex;
    gap: 5px;
    flex-wrap: wrap;
  }
  .tag {
    font-size: 11px;
    color: var(--text-soft);
    background: var(--surface-2);
    padding: 1px 7px;
    border-radius: 5px;
  }
}
</style>
