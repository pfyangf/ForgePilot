<template>
  <el-container class="shell">
    <el-aside width="220px">
      <div class="brand">ForgePilot</div>
      <el-menu :default-active="active" router>
        <el-menu-item index="/">工作台</el-menu-item>
        <el-menu-item index="/projects">项目管理</el-menu-item>
        <el-menu-item index="/coding">AI Coding</el-menu-item>
        <el-menu-item index="/delivery">测试与交付</el-menu-item>
        <el-menu-item index="/guidance">规约与文档</el-menu-item>
        <el-menu-item index="/activity">操作记录</el-menu-item>
        <el-menu-item index="/admin">平台管理</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">云端 AI Coding 平台 · 个人工作台骨架</el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const active = computed(() => {
  if (route.path.startsWith('/requirements') || route.path.startsWith('/tasks')) {
    return '/'
  }
  return route.path
})
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
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
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
