<script setup lang="ts">
import PageHeader from '@/components/layout/PageHeader.vue'

defineProps<{
  title: string
  desc?: string
}>()
</script>

<template>
  <div class="soon-page">
    <PageHeader :title="title" :description="desc" />
    <el-card shadow="never" class="card">
      <el-empty description="功能规划中">
        <template #image>
          <div class="illus">📋</div>
        </template>
        <p class="body">
          该模块已纳入产品路线图，将按「内容 → 链接 → 运营 → 系统」顺序交付。
          当前版本优先保障用户侧信息流与影视库能力。
        </p>
        <el-button type="primary" @click="$router.push('/dashboard')">返回概览</el-button>
      </el-empty>
    </el-card>
  </div>
</template>

<style scoped lang="scss">
.card {
  border: 1px solid var(--border);
  padding: 24px 0;
}

.illus {
  font-size: 72px;
  line-height: 1;
}

.body {
  max-width: 420px;
  margin: 0 auto 16px;
  color: var(--text-soft);
  font-size: 0.9rem;
  line-height: 1.6;
}
</style>
