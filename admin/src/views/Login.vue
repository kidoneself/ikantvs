<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/store/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const form = ref({ username: '', password: '' })
const loading = ref(false)

async function submit() {
  if (!form.value.username || !form.value.password) {
    ElMessage.warning('请填写用户名和密码')
    return
  }
  loading.value = true
  try {
    await auth.login(form.value.username, form.value.password)
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/dashboard'
    router.replace(redirect)
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <div class="panel">
      <div class="brand">
        <span class="logo">爱</span>
        <div>
          <h1>爱看后台</h1>
          <p>内容运营后台 · 与用户前台分离部署</p>
        </div>
      </div>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" size="large" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            size="large"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>
        <el-button type="primary" size="large" native-type="submit" :loading="loading" class="submit">
          登录
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<style scoped lang="scss">
.login {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(160deg, #0f172a 0%, #1e3a5f 50%, #0f172a 100%);
  padding: 24px;
}

.panel {
  width: 100%;
  max-width: 400px;
  background: #fff;
  border-radius: 12px;
  padding: 36px 32px;
  box-shadow: 0 24px 48px rgba(0, 0, 0, 0.25);
}

.brand {
  display: flex;
  gap: 14px;
  margin-bottom: 28px;

  .logo {
    width: 48px;
    height: 48px;
    border-radius: 10px;
    background: var(--brand);
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 1.25rem;
    font-weight: 700;
  }

  h1 {
    margin: 0 0 4px;
    font-size: 1.2rem;
  }

  p {
    margin: 0;
    font-size: 0.82rem;
    color: var(--text-muted);
  }
}

.submit {
  width: 100%;
  margin-top: 8px;
}
</style>
