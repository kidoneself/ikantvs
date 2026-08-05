<script setup lang="ts">
import CoverImage from '@/components/CoverImage.vue'
import type { TmdbDiscoverItem } from '@/api/content'

const props = defineProps<{
  item: TmdbDiscoverItem
  loading?: boolean
}>()

const emit = defineEmits<{ pick: [] }>()

const TYPE_LABEL: Record<string, string> = {
  movie: '电影',
  tv: '剧集',
}
</script>

<template>
  <article class="card" :class="{ loading }" @click="!loading && emit('pick')">
    <div class="poster">
      <CoverImage
        :title="item.title || '未命名'"
        :poster="item.poster"
        :category="TYPE_LABEL[item.type] || '电影'"
      />
      <span class="badge">{{ item.localId ? '已在库' : 'TMDB' }}</span>
      <span v-if="item.rating" class="rating">{{ Number(item.rating).toFixed(1) }}</span>
    </div>
    <div class="meta">
      <h3 class="title">{{ item.title }}</h3>
      <p class="sub">
        {{ item.year || '—' }}
        <template v-if="item.originalTitle && item.originalTitle !== item.title">
          · {{ item.originalTitle }}
        </template>
      </p>
      <p v-if="!item.localId" class="hint">点击添加并查看资源</p>
    </div>
    <div v-if="loading" class="overlay">添加中…</div>
  </article>
</template>

<style scoped lang="scss">
.card {
  position: relative;
  background: var(--surface);
  border-radius: var(--radius);
  overflow: hidden;
  cursor: pointer;
  box-shadow: var(--shadow-s);
  border: 1px dashed var(--border);
  transition: transform 0.18s, box-shadow 0.18s, border-color 0.18s;

  &:hover:not(.loading) {
    transform: translateY(-4px);
    box-shadow: var(--shadow-hover);
    border-color: var(--brand-soft);
  }
  &.loading {
    cursor: wait;
    opacity: 0.85;
  }
}

.poster {
  position: relative;
  aspect-ratio: 5 / 6;
  overflow: hidden;
  background: var(--surface-2);

  .badge {
    position: absolute;
    top: 8px;
    left: 8px;
    padding: 2px 8px;
    border-radius: 6px;
    font-size: 11px;
    font-weight: 700;
    background: rgba(0, 0, 0, 0.62);
    color: #fff;
    backdrop-filter: blur(4px);
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
}

.meta {
  padding: 10px 12px 12px;
  .title {
    margin: 0;
    font-size: 14px;
    font-weight: 700;
    line-height: 1.35;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  .sub {
    margin: 4px 0 0;
    font-size: 12px;
    color: var(--text-muted);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .hint {
    margin: 6px 0 0;
    font-size: 11px;
    color: var(--brand);
  }
}

.overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.72);
  font-size: 14px;
  font-weight: 600;
  color: var(--brand-strong);
}
</style>
