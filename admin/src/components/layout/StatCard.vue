<script setup lang="ts">
defineProps<{
  label: string
  value: string | number
  hint?: string
  /** 环比文案，如 +12.3% / 新增 */
  change?: string
  /** up=涨绿 down=跌红 flat=灰 */
  changeTone?: 'up' | 'down' | 'flat'
  icon?: string
  type?: 'primary' | 'success' | 'warning' | 'info'
}>()
</script>

<template>
  <div class="stat-card" :class="type || 'primary'">
    <div v-if="icon" class="icon">
      <el-icon><component :is="icon" /></el-icon>
    </div>
    <div class="body">
      <div class="label">{{ label }}</div>
      <div class="value-row">
        <div class="value">{{ value }}</div>
        <span v-if="change" class="change" :class="changeTone || 'flat'">{{ change }}</span>
      </div>
      <div v-if="hint" class="hint">{{ hint }}</div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 20px;
  border-radius: var(--radius);
  background: var(--surface);
  border: 1px solid var(--border);
  box-shadow: var(--shadow-s);
  transition: transform 0.18s ease, box-shadow 0.18s ease;
  position: relative;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 4px;
    background: var(--accent);
  }

  &:hover {
    transform: translateY(-2px);
    box-shadow: var(--shadow-m);
  }

  .icon {
    width: 46px;
    height: 46px;
    border-radius: 12px;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    font-size: 22px;
    color: var(--accent);
    background: var(--accent-soft);
  }

  .body {
    min-width: 0;
  }

  .label {
    font-size: 0.82rem;
    color: var(--text-muted);
    margin-bottom: 4px;
  }

  .value-row {
    display: flex;
    align-items: baseline;
    gap: 8px;
    flex-wrap: wrap;
  }

  .value {
    font-size: 1.7rem;
    font-weight: 700;
    line-height: 1.1;
    letter-spacing: -0.01em;
    color: var(--text);
  }

  .change {
    font-size: 0.82rem;
    font-weight: 600;
    &.up {
      color: #16a34a;
    }
    &.down {
      color: #dc2626;
    }
    &.flat {
      color: var(--text-soft);
    }
  }

  .hint {
    margin-top: 5px;
    font-size: 0.78rem;
    color: var(--text-soft);
  }

  &.primary {
    --accent: #4f46e5;
    --accent-soft: #eef2ff;
  }
  &.success {
    --accent: #16a34a;
    --accent-soft: #ecfdf3;
  }
  &.warning {
    --accent: #d97706;
    --accent-soft: #fff7ed;
  }
  &.info {
    --accent: #0ea5e9;
    --accent-soft: #ecfeff;
  }
}
</style>
