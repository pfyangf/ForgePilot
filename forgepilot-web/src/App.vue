<template>
  <router-view v-if="route.name === 'login'" />
  <el-container v-else class="shell">
    <el-aside class="aside" :width="asideWidth">
      <div class="brand">
        <div class="brand__mark" aria-hidden="true">FP</div>
        <div v-if="!collapsed" class="brand__text">ForgePilot</div>
        <button class="brand__toggle" type="button" :title="collapsed ? '展开菜单' : '收起菜单'" @click="toggleCollapsed">
          <el-icon><component :is="collapsed ? Expand : Fold" /></el-icon>
        </button>
      </div>
      <el-menu
        :default-active="active"
        :collapse="collapsed"
        :collapse-transition="false"
        router
        class="menu"
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="iconMap[item.icon] || Document" /></el-icon>
          <template #title>{{ item.label }}</template>
        </el-menu-item>
      </el-menu>
      <div class="aside-footer">
        <div v-if="auth.user" class="user-card" :class="{ 'user-card--collapsed': collapsed }">
          <div class="user-card__avatar">{{ avatarText }}</div>
          <div v-if="!collapsed" class="user-card__meta">
            <div class="user-card__role">{{ roleLabel }}</div>
            <div class="user-card__name">{{ auth.user.username }}</div>
          </div>
        </div>
      </div>
    </el-aside>

    <el-container class="main-wrap">
      <el-header class="header" height="52px">
        <span class="header__title">云端 AI Coding 平台 · 个人工作台</span>
        <div class="header__actions" v-if="auth.user">
          <el-popover placement="bottom-end" :width="320" trigger="click">
            <template #reference>
              <button class="header__link" type="button">
                通知
                <span class="header__badge">3</span>
              </button>
            </template>
            <div class="stub-panel">
              <div class="stub-panel__title">通知</div>
              <p class="stub-panel__empty">暂无新通知。后续会接入任务审核、执行异常等公共提醒。</p>
            </div>
          </el-popover>
          <el-popover placement="bottom-end" :width="320" trigger="click">
            <template #reference>
              <button class="header__link" type="button">公告</button>
            </template>
            <div class="stub-panel">
              <div class="stub-panel__title">公告</div>
              <p class="stub-panel__empty">暂无公告。平台级通知与版本说明会放在这里。</p>
            </div>
          </el-popover>
          <el-dropdown trigger="click">
            <button class="header__user" type="button">
              {{ auth.user.displayName }}（{{ auth.user.username }}）
              <el-icon class="header__caret"><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>{{ roleLabel }}</el-dropdown-item>
                <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <button class="header__link header__logout" type="button" @click="logout">退出</button>
        </div>
      </el-header>
      <el-main class="content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowDown,
  Connection,
  Cpu,
  Document,
  Expand,
  Fold,
  Folder,
  MagicStick,
  Notebook,
  Odometer,
  Setting,
  Upload,
} from '@element-plus/icons-vue'
import { visibleSidebarItems } from '@/domain/menus'
import { useAuthStore } from '@/stores/auth'

const COLLAPSE_KEY = 'fp.sidebar.collapsed'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const collapsed = ref(false)

const iconMap: Record<string, object> = {
  Odometer,
  Folder,
  MagicStick,
  Upload,
  Document,
  Cpu,
  Notebook,
  Connection,
  Setting,
}

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
const asideWidth = computed(() => (collapsed.value ? '64px' : '208px'))

const roleLabel = computed(() => {
  const roles = auth.user?.roles ?? []
  if (roles.includes('PLATFORM_ADMIN')) return '平台管理员'
  if (roles.includes('TENANT_ADMIN')) return '管理员'
  if (roles.includes('PROJECT_ADMIN')) return '项目管理员'
  return roles[0] || '用户'
})

const avatarText = computed(() => {
  const name = auth.user?.displayName || auth.user?.username || '?'
  return name.slice(0, 1).toUpperCase()
})

onMounted(() => {
  collapsed.value = localStorage.getItem(COLLAPSE_KEY) === '1'
  void auth.restoreSession()
})

watch(collapsed, (value) => {
  localStorage.setItem(COLLAPSE_KEY, value ? '1' : '0')
})

function toggleCollapsed() {
  collapsed.value = !collapsed.value
}

async function logout() {
  auth.logout()
  await router.push('/login')
}
</script>

<style scoped>
.shell {
  min-height: 100vh;
  background: var(--fp-color-bg);
}

.aside {
  display: flex;
  flex-direction: column;
  background: var(--fp-color-sidebar);
  color: var(--fp-color-sidebar-text);
  overflow: hidden;
  transition: width 0.2s ease;
}

.brand {
  height: var(--fp-header-height);
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.16);
}

.brand__mark {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  background: var(--fp-color-primary);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
}

.brand__text {
  flex: 1;
  min-width: 0;
  font-size: 15px;
  font-weight: 700;
  color: #fff;
  white-space: nowrap;
  overflow: hidden;
}

.brand__toggle {
  margin-left: auto;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 6px;
  background: rgba(148, 163, 184, 0.12);
  color: #e2e8f0;
  cursor: pointer;
  display: grid;
  place-items: center;
}

.brand__toggle:hover {
  background: rgba(148, 163, 184, 0.22);
}

.menu {
  flex: 1;
  border-right: none !important;
  background: transparent;
  padding: 10px 8px;
  overflow-y: auto;
}

.menu:not(.el-menu--collapse) {
  width: 100%;
}

:deep(.el-menu) {
  --el-menu-bg-color: transparent;
  --el-menu-text-color: #cbd5e1;
  --el-menu-hover-bg-color: rgba(47, 107, 255, 0.16);
  --el-menu-active-color: #ffffff;
  border-right: none;
}

:deep(.el-menu-item) {
  height: 40px;
  line-height: 40px;
  margin: 2px 0;
  border-radius: 8px;
  color: #cbd5e1;
}

:deep(.el-menu-item .el-icon) {
  color: inherit;
}

:deep(.el-menu-item.is-active) {
  background: var(--fp-color-primary) !important;
  color: #fff !important;
}

:deep(.el-menu-item:hover) {
  background: rgba(47, 107, 255, 0.16);
}

:deep(.el-menu--collapse .el-menu-item) {
  padding: 0 !important;
  justify-content: center;
}

.aside-footer {
  padding: 12px;
  border-top: 1px solid rgba(148, 163, 184, 0.16);
}

.user-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.35);
}

.user-card--collapsed {
  justify-content: center;
  padding: 6px;
}

.user-card__avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: rgba(47, 107, 255, 0.35);
  color: #fff;
  display: grid;
  place-items: center;
  font-size: 13px;
  font-weight: 600;
  flex: 0 0 auto;
}

.user-card__meta {
  min-width: 0;
}

.user-card__role {
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  line-height: 1.2;
}

.user-card__name {
  font-size: 12px;
  color: var(--fp-color-sidebar-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
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
  padding: 0 20px;
  background: var(--fp-color-surface);
  border-bottom: 1px solid var(--fp-color-border);
}

.header__title {
  color: var(--fp-color-text-secondary);
  font-size: 14px;
}

.header__actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.header__link,
.header__user {
  border: 0;
  background: transparent;
  color: var(--fp-color-text);
  font-size: 14px;
  height: 32px;
  padding: 0 10px;
  border-radius: 6px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.header__link:hover,
.header__user:hover {
  background: #f2f3f5;
}

.header__badge {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--fp-color-primary);
  color: #fff;
  font-size: 12px;
  line-height: 18px;
  text-align: center;
}

.header__caret {
  font-size: 12px;
  color: var(--fp-color-muted);
}

.header__logout {
  color: var(--fp-color-text-secondary);
}

.content {
  padding: 16px 20px 20px;
}

.stub-panel__title {
  font-weight: 600;
  color: var(--fp-color-ink);
  margin-bottom: 8px;
}

.stub-panel__empty {
  margin: 0;
  color: var(--fp-color-text-secondary);
  font-size: 13px;
  line-height: 1.5;
}
</style>
