<template>
  <el-config-provider :locale="elementPlusLocale">
  <router-view v-if="route.name === 'login'" />
  <el-container v-else class="shell">
    <el-aside class="aside" :class="{ 'aside--collapsed': collapsed }" :width="asideWidth">
      <div class="brand">
        <template v-if="!collapsed">
          <div class="brand__mark" aria-hidden="true">FP</div>
          <div class="brand__text">ForgePilot</div>
          <button class="brand__toggle" type="button" title="收起菜单" @click="toggleCollapsed">
            <el-icon><Fold /></el-icon>
          </button>
        </template>
        <button
          v-else
          class="brand__toggle brand__toggle--solo"
          type="button"
          title="展开菜单"
          @click="toggleCollapsed"
        >
          <el-icon><Expand /></el-icon>
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
        <span class="header__title">云端 AI Coding 研发平台</span>
        <div class="header__actions" v-if="auth.user">
          <button
            class="header__link header__theme"
            type="button"
            :aria-label="theme === 'light' ? '切换到深色主题' : '切换到浅色主题'"
            :title="theme === 'light' ? '深色主题' : '浅色主题'"
            @click="toggleTheme"
          >
            <el-icon>
              <Sunny v-if="theme === 'dark'" />
              <Moon v-else />
            </el-icon>
          </button>
          <HelpTip :text="HELP.themeSwitch" />
          <el-popover placement="bottom-end" :width="320" trigger="click">
            <template #reference>
              <button class="header__link header__notice" type="button">
                <el-icon><Bell /></el-icon>
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
  </el-config-provider>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowDown,
  Bell,
  Connection,
  Cpu,
  Document,
  Expand,
  Fold,
  Folder,
  MagicStick,
  Moon,
  Notebook,
  Odometer,
  Setting,
  Sunny,
  Upload,
} from '@element-plus/icons-vue'
import HelpTip from '@/components/HelpTip.vue'
import { HELP } from '@/domain/help'
import { elementPlusLocale } from '@/domain/locale'
import { visibleSidebarItems } from '@/domain/menus'
import { useTheme } from '@/composables/useTheme'
import { useAuthStore } from '@/stores/auth'

const COLLAPSE_KEY = 'fp.sidebar.collapsed'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const collapsed = ref(false)
const { theme, toggleTheme } = useTheme()

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
  flex: 0 0 208px;
  width: 208px !important;
  max-width: 208px;
  min-width: 208px;
  background: linear-gradient(180deg, var(--fp-color-sidebar-start) 0%, var(--fp-color-sidebar) 100%);
  color: var(--fp-color-sidebar-text);
  border-right: 1px solid var(--fp-color-sidebar-border);
  overflow: hidden;
  box-sizing: border-box;
  transition: width 0.2s ease, flex-basis 0.2s ease, max-width 0.2s ease, min-width 0.2s ease;
}

.aside--collapsed {
  flex-basis: 64px;
  width: 64px !important;
  max-width: 64px;
  min-width: 64px;
}

.brand {
  height: var(--fp-header-height);
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  border-bottom: 1px solid var(--fp-color-sidebar-border);
  box-sizing: border-box;
  overflow: hidden;
}

.aside--collapsed .brand {
  justify-content: center;
  padding: 0;
}

.brand__mark {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  background: linear-gradient(180deg, #5b8aff, #3b6cf0);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  box-shadow: 0 6px 14px rgba(76, 125, 255, 0.35);
}

.brand__text {
  flex: 1;
  min-width: 0;
  font-size: 15px;
  font-weight: 700;
  color: var(--fp-color-ink);
  white-space: nowrap;
  overflow: hidden;
}

.brand__toggle {
  margin-left: auto;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 6px;
  background: var(--fp-color-help-bg);
  color: var(--fp-color-sidebar-text);
  cursor: pointer;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
}

.brand__toggle--solo {
  margin-left: 0;
}

.brand__toggle:hover {
  background: rgba(148, 163, 184, 0.22);
}

.menu {
  flex: 1;
  border-right: none !important;
  background: transparent;
  padding: 10px 8px;
  overflow-x: hidden;
  overflow-y: auto;
  box-sizing: border-box;
}

.aside--collapsed .menu {
  padding: 10px 6px;
  width: 100% !important;
}

.menu:not(.el-menu--collapse) {
  width: 100%;
}

:deep(.el-menu) {
  --el-menu-bg-color: transparent;
  --el-menu-text-color: var(--fp-color-sidebar-muted);
  --el-menu-hover-bg-color: var(--fp-color-sidebar-hover);
  --el-menu-active-color: var(--fp-color-ink);
  border-right: none;
}

:deep(.el-menu--collapse) {
  width: 100% !important;
}

:deep(.el-menu-item) {
  height: 40px;
  line-height: 40px;
  margin: 2px 0;
  border-radius: 8px;
  color: var(--fp-color-sidebar-muted);
}

:deep(.el-menu-item .el-icon) {
  color: inherit;
  margin: 0;
}

:deep(.el-menu-item.is-active) {
  background: var(--fp-color-sidebar-active) !important;
  color: var(--fp-color-ink) !important;
  box-shadow: inset 3px 0 0 #4c7dff;
}

:deep(.el-menu-item:hover) {
  background: var(--fp-color-sidebar-hover);
  color: var(--fp-color-ink);
}

:deep(.el-menu--collapse .el-menu-item) {
  padding: 0 !important;
  justify-content: center;
  width: 100%;
}

:deep(.el-menu--collapse .el-menu-item .el-icon) {
  margin: 0;
}

.aside-footer {
  padding: 12px;
  border-top: 1px solid var(--fp-color-sidebar-border);
  box-sizing: border-box;
}

.aside--collapsed .aside-footer {
  padding: 10px 6px;
}

.user-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: 8px;
  background: var(--fp-color-header-btn-bg);
  border: 1px solid var(--fp-color-sidebar-border);
  overflow: hidden;
}

.user-card--collapsed {
  justify-content: center;
  padding: 6px 0;
  background: transparent;
}

.user-card__avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: rgba(76, 125, 255, 0.35);
  color: var(--fp-color-ink);
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
  color: var(--fp-color-ink);
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
  flex: 1;
  background: var(--fp-color-bg);
}

.header {
  height: var(--fp-header-height);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background: var(--fp-color-header-bg);
  border-bottom: 1px solid var(--fp-color-sidebar-border);
  color: var(--fp-color-text-secondary);
  backdrop-filter: blur(12px);
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
  border: 1px solid var(--fp-color-header-btn-border);
  background: var(--fp-color-header-btn-bg);
  color: var(--fp-color-header-btn-text);
  font-size: 13px;
  height: 32px;
  padding: 0 10px;
  border-radius: 8px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.header__link:hover,
.header__user:hover {
  background: var(--fp-color-header-btn-hover);
  color: var(--fp-color-header-btn-hover-text);
}

.header__theme {
  width: 32px;
  padding: 0;
  justify-content: center;
}

.header__badge {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--fp-color-primary);
  color: var(--fp-color-ink);
  font-size: 12px;
  line-height: 18px;
  text-align: center;
}

.header__caret {
  font-size: 12px;
  color: var(--fp-color-muted);
}

.header__logout {
  color: var(--fp-color-muted);
}

.content {
  padding: 16px 20px 20px;
  background-color: transparent;
  background-image:
    radial-gradient(900px 480px at 12% -10%, var(--fp-color-glow-primary), transparent 58%),
    radial-gradient(640px 280px at 100% 0%, var(--fp-color-glow-success), transparent 55%),
    linear-gradient(var(--fp-color-grid-line) 1px, transparent 1px),
    linear-gradient(90deg, var(--fp-color-grid-line) 1px, transparent 1px),
    linear-gradient(180deg, var(--fp-color-content-start) 0%, var(--fp-color-bg) 48%, var(--fp-color-bg) 100%);
  background-size: auto, auto, 24px 24px, 24px 24px, auto;
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
