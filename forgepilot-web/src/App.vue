<template>
  <router-view v-if="route.name === 'login'" />
  <el-container v-else class="shell">
    <el-aside width="220px">
      <div class="brand">ForgePilot</div>
      <el-menu :default-active="active" router>
        <el-menu-item index="/">工作台</el-menu-item>
        <el-menu-item index="/projects">项目管理</el-menu-item>
        <el-menu-item index="/coding">AI Coding</el-menu-item>
        <el-menu-item index="/delivery">测试与交付</el-menu-item>
        <el-menu-item index="/guidance">规约与文档</el-menu-item>
        <el-menu-item index="/catalog">模型与工具</el-menu-item>
        <el-menu-item index="/activity">操作记录</el-menu-item>
        <el-menu-item index="/nodes">执行节点</el-menu-item>
        <el-menu-item index="/admin">平台管理</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span>云端 AI Coding 平台 · 个人工作台</span>
        <div class="user" v-if="auth.user">
          <span>{{ auth.user.displayName }}（{{ auth.user.username }}）</span>
          <el-button link type="primary" @click="logout">退出</el-button>
        </div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const active = computed(() => {
  if (route.path.startsWith('/requirements') || route.path.startsWith('/tasks')) {
    return '/'
  }
  if (route.path.startsWith('/projects')) {
    return '/projects'
  }
  return route.path
})

onMounted(() => {
  void auth.restoreSession()
})

async function logout() {
  auth.logout()
  await router.push('/login')
}
</script>

<style>
body {
  margin: 0;
  font-family: 'Segoe UI', 'PingFang SC', sans-serif;
  background: #f5f7fa;
}
.shell {
  min-height: 100vh;
}
.brand {
  padding: 20px 16px 8px;
  font-weight: 700;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
}
.user {
  display: flex;
  gap: 8px;
  align-items: center;
}
.log,
.diff {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  white-space: pre-wrap;
  background: #0f172a;
  color: #e2e8f0;
  padding: 12px;
  border-radius: 8px;
  max-height: 240px;
  overflow: auto;
}
.diff-add {
  color: #86efac;
}
.diff-del {
  color: #fca5a5;
}
</style>
