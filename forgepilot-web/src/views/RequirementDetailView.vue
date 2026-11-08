<template>
  <div v-if="requirement">
    <el-page-header @back="router.push('/')" content="需求详情" />
    <el-descriptions :column="2" border class="block">
      <el-descriptions-item label="名称">{{ requirement.title }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ requirement.status }}</el-descriptions-item>
      <el-descriptions-item label="项目">{{ requirement.projectName }}</el-descriptions-item>
      <el-descriptions-item label="进度">{{ progress.done }}/{{ progress.total }}</el-descriptions-item>
    </el-descriptions>

    <el-card v-if="inAnalysis" class="block">
      <template #header>分析草稿与任务拆分</template>
      <p>这里确认的是草稿，不是 IDE，也不是正式审批流。</p>
      <el-input v-model="requirement.draft" type="textarea" :rows="8" :disabled="requirement.confirmed" />
      <el-table :data="requirement.suggestions" empty-text="还没有拆分建议" class="block">
        <el-table-column prop="title" label="建议任务" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="danger" :disabled="requirement.confirmed" @click="store.removeSuggestion(requirement.id, row.id)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="row">
        <el-input v-model="suggestion" placeholder="补充一条任务" :disabled="requirement.confirmed" />
        <el-button :disabled="requirement.confirmed" @click="addSuggestion">添加</el-button>
        <el-button type="primary" :disabled="requirement.confirmed" @click="store.confirmRequirement(requirement.id)">
          确认分析与拆分
        </el-button>
      </div>
    </el-card>

    <el-card v-else class="block">
      <template #header>需求下的任务</template>
      <p>执行、验证和审核在任务详情里看，工作台不另存一份状态。</p>
      <el-table :data="related" @row-click="openTask">
        <el-table-column prop="title" label="任务" />
        <el-table-column prop="status" label="状态" width="180" />
      </el-table>
    </el-card>
  </div>
  <el-empty v-else description="没有这条需求">
    <el-button @click="router.push('/')">返回工作台</el-button>
  </el-empty>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useWorkbenchStore } from '@/stores/workbench'

const props = defineProps<{ id: string }>()
const store = useWorkbenchStore()
const router = useRouter()
const suggestion = ref('')
const requirement = computed(() => store.requirementById(props.id))
const related = computed(() => store.tasksFor(props.id))
const progress = computed(() => store.progress(props.id))
const inAnalysis = computed(() => requirement.value?.status === '待分析' || requirement.value?.status === '设计中')

onMounted(() => {
  if (!requirement.value) {
    void store.load('member')
  }
})

function addSuggestion() {
  store.addSuggestion(props.id, suggestion.value)
  suggestion.value = ''
}

function openTask(row: { id: string }) {
  router.push(`/tasks/${row.id}`)
}
</script>

<style scoped>
.block {
  margin-top: 16px;
}
.row {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}
</style>
