<template>
  <el-card>
    <template #header>测试与交付</template>
    <p>待审核和已完成的任务仍从工作台那份状态里筛出来。</p>
    <el-table :data="rows" empty-text="当前筛选下没有交付条目" @row-click="openTask">
      <el-table-column prop="title" label="任务" />
      <el-table-column prop="status" label="状态" width="180" />
      <el-table-column label="Draft MR" width="120">
        <template #default="{ row }">{{ row.mergeRequests.length }}</template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useWorkbenchStore } from '@/stores/workbench'

const store = useWorkbenchStore()
const router = useRouter()
const rows = computed(() =>
  store.visibleTasks.filter((task) => task.status === 'WAITING_APPROVAL' || task.status === 'COMPLETED' || task.status === 'VERIFYING'),
)

function openTask(row: { id: string }) {
  router.push(`/tasks/${row.id}`)
}
</script>
