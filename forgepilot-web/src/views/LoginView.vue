<template>
  <div class="login">
    <el-card class="card" shadow="never">
      <div class="brand">ForgePilot</div>
      <p class="subtitle">云端 AI Coding 平台</p>
      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="username" autocomplete="username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="password"
            type="password"
            show-password
            autocomplete="current-password"
            placeholder="请输入密码"
          />
        </el-form-item>
        <el-alert v-if="error" type="error" :title="error" show-icon :closable="false" class="error" />
        <el-form-item>
          <el-button type="primary" native-type="submit" :loading="loading" class="submit">登录</el-button>
        </el-form-item>
      </el-form>
      <p class="hint">本机演示：工作台用 demo_member，维护模型目录用 demo_admin，密码均为 ForgePilot!dev</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const username = ref('demo_member')
const password = ref('ForgePilot!dev')
const loading = ref(false)
const error = ref('')

async function submit() {
  loading.value = true
  error.value = ''
  try {
    await auth.login(username.value.trim(), password.value)
    await router.replace('/')
  } catch (err) {
    error.value = err instanceof ApiError ? err.message : '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: var(--fp-space-5);
  background: var(--fp-color-bg);
}

.card {
  width: min(400px, 100%);
  border: 1px solid var(--fp-color-border);
  border-radius: var(--fp-radius);
}

.card :deep(.el-card__body) {
  padding: 32px 28px 24px;
}

.brand {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: var(--fp-color-text);
}

.subtitle {
  margin: 6px 0 24px;
  color: var(--fp-color-text-secondary);
  font-size: 14px;
}

.error {
  margin-bottom: var(--fp-space-4);
}

.submit {
  width: 100%;
}

.hint {
  margin: 4px 0 0;
  color: var(--fp-color-muted);
  font-size: 13px;
  line-height: 1.4;
}

.card :deep(.el-form-item) {
  margin-bottom: 18px;
}

.card :deep(.el-form-item__label) {
  margin-bottom: 6px;
  line-height: 1.2;
  color: var(--fp-color-text-secondary);
}
</style>
