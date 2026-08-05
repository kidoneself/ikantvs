<script setup lang="ts">
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<template>
  <div class="container notfound">
    <div class="code">404</div>
    <p class="msg">页面走丢了，或资源已下架</p>
    <div class="acts">
      <el-button type="primary" @click="router.push('/')">返回首页</el-button>
      <el-button @click="router.push({ name: 'find' })">去搜索</el-button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.notfound {
  min-height: 60vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}
.code {
  font-size: 96px;
  font-weight: 800;
  line-height: 1;
  background: linear-gradient(120deg, var(--brand), #3ad29f);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}
.msg {
  margin: 14px 0 22px;
  font-size: 16px;
  color: var(--text-soft);
}
.acts {
  display: flex;
  gap: 12px;
}
</style>
