<template>
  <el-card>
    <template #header>测试与交付</template>
    <p>审核与验证证据仍挂在任务上。平台不会自动合并 Draft MR。</p>
    <el-table v-loading="store.loading" :data="approvalTasks" @row-click="openTask">
      <el-table-column prop="title" label="任务" />
      <el-table-column prop="status" label="状态" width="180" />
      <el-table-column prop="projectName" label="项目" width="120" />
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useWorkbenchStore } from '@/stores/workbench'

const store = useWorkbenchStore()
const router = useRouter()
const approvalTasks = computed(() =>
  store.visibleTasks.filter((task) => task.status === 'WAITING_APPROVAL' || task.status === 'VERIFYING' || task.status === 'COMPLETED'),
)

onMounted(() => {
  void store.load('member')
})

function openTask(row: { id: string }) {
  router.push(`/tasks/${row.id}`)
}
</script>
