<template>
  <router-view v-if="route.name === 'login'" />
  <el-container v-else class="shell">
    <el-aside class="aside" width="220px">
      <div class="brand">ForgePilot</div>
      <el-menu :default-active="active" router class="menu">
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          {{ item.label }}
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container class="main-wrap">
      <el-header class="header">
        <span class="title">云端 AI Coding 平台 · 个人工作台</span>
        <div class="user" v-if="auth.user">
          <span>{{ auth.user.displayName }}（{{ auth.user.username }}）</span>
          <el-button link type="primary" @click="logout">退出</el-button>
        </div>
      </el-header>
      <el-main class="content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { visibleSidebarItems } from '@/domain/menus'
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

const menuItems = computed(() => visibleSidebarItems(auth.user?.menus))

onMounted(() => {
  void auth.restoreSession()
})

async function logout() {
  auth.logout()
  await router.push('/login')
}
</script>

<style scoped>
.shell {
  min-height: 100vh;
}

.aside {
  background: var(--fp-color-surface);
  border-right: 1px solid var(--fp-color-border);
  overflow-x: hidden;
}

.brand {
  height: var(--fp-header-height);
  display: flex;
  align-items: center;
  padding: 0 var(--fp-space-4);
  font-size: 16px;
  font-weight: 700;
  color: var(--fp-color-text);
  border-bottom: 1px solid var(--fp-color-border);
}

.menu {
  border-right: none;
}

.main-wrap {
  min-width: 0;
  background: var(--fp-color-bg);
}

.header {
  height: var(--fp-header-height);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--fp-space-5);
  background: var(--fp-color-surface);
  border-bottom: 1px solid var(--fp-color-border);
}

.title {
  color: var(--fp-color-text-secondary);
  font-size: 14px;
}

.user {
  display: flex;
  gap: var(--fp-space-2);
  align-items: center;
  color: var(--fp-color-text);
  font-size: 14px;
}

.content {
  padding: var(--fp-space-5);
}

</style>
