<template>
  <div class="login">
    <el-card class="card">
      <template #header>登录 ForgePilot</template>
      <el-form @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-alert v-if="error" type="error" :title="error" show-icon :closable="false" class="error" />
        <el-button type="primary" native-type="submit" :loading="loading" style="width: 100%">登录</el-button>
      </el-form>
      <p class="hint">本机演示：demo_member / ForgePilot!dev</p>
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
  background: #f5f7fa;
}
.card {
  width: 420px;
}
.error {
  margin-bottom: 12px;
}
.hint {
  margin-top: 16px;
  color: #6b7280;
  font-size: 13px;
}
</style>
