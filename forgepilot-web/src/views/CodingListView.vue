<template>
  <el-card>
    <template #header>AI Coding</template>
    <p>这里和工作台打开的是同一批任务，不是另一套执行状态。</p>
    <el-table :data="store.visibleTasks" @row-click="openTask">
      <el-table-column prop="title" label="任务" />
      <el-table-column prop="status" label="状态" width="180" />
      <el-table-column prop="branchName" label="平台分支" />
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useWorkbenchStore } from '@/stores/workbench'

const store = useWorkbenchStore()
const router = useRouter()

function openTask(row: { id: string }) {
  router.push(`/tasks/${row.id}`)
}
</script>
