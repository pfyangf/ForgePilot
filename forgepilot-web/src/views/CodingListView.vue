<template>
  <div>
    <div class="page-header">
      <div class="page-header__titles">
        <h1 class="page-header__title">AI Coding</h1>
        <p class="page-header__subtitle">和工作台打开的是同一批任务。平台托管执行，不会假装编码已经完成。</p>
      </div>
    </div>
    <el-card shadow="never">
      <el-table v-loading="store.loading" :data="store.visibleTasks" @row-click="openTask">
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
        <el-table-column label="工作分支" min-width="180">
          <template #default="{ row }">
            <BranchPill :name="row.branchName" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import BranchPill from '@/components/BranchPill.vue'
import { statusTone, taskStatusLabel } from '@/domain/labels'
import { useWorkbenchStore } from '@/stores/workbench'

const store = useWorkbenchStore()
const router = useRouter()

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
