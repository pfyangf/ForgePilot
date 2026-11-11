<template>
  <div>
    <div class="page-header">
      <div class="page-header__titles">
        <h1 class="page-header__title">测试与交付</h1>
        <p class="page-header__subtitle">审核与验证证据仍挂在任务上。平台不会自动合并 Draft 合并请求。</p>
      </div>
    </div>
    <el-card shadow="never">
      <el-table v-loading="store.loading" :data="approvalTasks" @row-click="openTask">
        <el-table-column label="任务" min-width="260">
          <template #default="{ row }">
            <div class="name-cell">
              <span v-if="row.code" class="fp-code-chip fp-code-chip--task">{{ row.code }}</span>
              <span>{{ row.title }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <span class="fp-status-tag" :class="toneClass(row.status)">{{ taskStatusLabel(row.status) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="projectName" label="项目" width="160" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { statusTone, taskStatusLabel } from '@/domain/labels'
import { useWorkbenchStore } from '@/stores/workbench'

const store = useWorkbenchStore()
const router = useRouter()
const approvalTasks = computed(() =>
  store.visibleTasks.filter((task) => task.status === 'WAITING_APPROVAL' || task.status === 'VERIFYING' || task.status === 'COMPLETED'),
)

onMounted(() => {
  void store.load('member')
})

function toneClass(status: string) {
  const tone = statusTone(status)
  return tone ? `is-${tone}` : ''
}

function openTask(row: { id: string }) {
  router.push(`/tasks/${row.id}`)
}
</script>

<style scoped>
.name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
