<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <header class="page-header">
    <div class="title-wrap">
      <span class="accent" />
      <div>
        <h1>{{ title }}</h1>
        <p v-if="description" class="desc">{{ description }}</p>
      </div>
    </div>
    <div v-if="$slots.extra" class="extra">
      <slot name="extra" />
    </div>
  </header>
</template>

<style scoped lang="scss">
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 22px;

  .title-wrap {
    display: flex;
    align-items: stretch;
    gap: 12px;
  }

  .accent {
    width: 4px;
    border-radius: 4px;
    background: linear-gradient(180deg, #6366f1, #4f46e5);
    flex-shrink: 0;
  }

  h1 {
    margin: 0 0 4px;
    font-size: 1.4rem;
    font-weight: 700;
    letter-spacing: -0.01em;
    color: var(--text);
  }

  .desc {
    margin: 0;
    font-size: 0.86rem;
    color: var(--text-muted);
  }

  .extra {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
  }
}
</style>
