<template>
  <div v-if="requirement">
    <el-page-header @back="router.push('/')" content="需求详情" />
    <el-alert v-if="store.actionError" class="block" type="error" show-icon :closable="false" :title="store.actionError" />
    <el-descriptions :column="2" border class="block">
      <el-descriptions-item label="名称">{{ requirement.title }}</el-descriptions-item>
      <el-descriptions-item label="状态">{{ requirement.status }}</el-descriptions-item>
      <el-descriptions-item label="项目">{{ requirement.projectName }}</el-descriptions-item>
      <el-descriptions-item label="进度">{{ progress.done }}/{{ progress.total }}</el-descriptions-item>
    </el-descriptions>

    <el-card v-if="inAnalysis" class="block">
      <template #header>分析草稿与任务拆分</template>
      <p>确认会写入服务器，并把需求标成「待开发」。若下面有建议任务，确认时会创建真实任务并冻结当前项目仓库集合。</p>
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
        <el-button type="primary" :loading="saving" :disabled="requirement.confirmed" @click="confirm">
          确认分析与拆分
        </el-button>
      </div>
    </el-card>

    <el-card v-else class="block">
      <template #header>
        <div class="row-between">
          <span>需求下的任务</span>
          <el-button type="primary" link @click="showCreate = true">新建任务</el-button>
        </div>
      </template>
      <p>执行、验证和审核在任务详情里看。创建任务时会冻结项目当前仓库集合，不会创建 Agent Session。</p>
      <el-table :data="related" @row-click="openTask">
        <el-table-column prop="title" label="任务" />
        <el-table-column prop="status" label="状态" width="180" />
        <el-table-column prop="branchName" label="分支" width="280" />
      </el-table>
    </el-card>

    <el-dialog v-model="showCreate" title="新建任务" width="480px">
      <el-input v-model="newTaskTitle" placeholder="任务标题" />
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="createTask">创建</el-button>
      </template>
    </el-dialog>
  </div>
  <el-empty v-else description="没有这条需求">
    <el-button @click="router.push('/')">返回工作台</el-button>
  </el-empty>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useWorkbenchStore } from '@/stores/workbench'

const props = defineProps<{ id: string }>()
const store = useWorkbenchStore()
const router = useRouter()
const suggestion = ref('')
const showCreate = ref(false)
const newTaskTitle = ref('')
const saving = ref(false)
const requirement = computed(() => store.requirementById(props.id))
const related = computed(() => store.tasksFor(props.id))
const progress = computed(() => store.progress(props.id))
const inAnalysis = computed(() => requirement.value?.status === '待分析' || requirement.value?.status === '设计中')

onMounted(async () => {
  try {
    await store.loadRequirementDetail(props.id)
  } catch {
    await store.load('member')
  }
})

function addSuggestion() {
  store.addSuggestion(props.id, suggestion.value)
  suggestion.value = ''
}

async function confirm() {
  saving.value = true
  try {
    await store.confirmRequirement(props.id)
    ElMessage.success('已确认分析与拆分')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '确认失败')
  } finally {
    saving.value = false
  }
}

async function createTask() {
  if (!newTaskTitle.value.trim()) {
    ElMessage.warning('请填写任务标题')
    return
  }
  saving.value = true
  try {
    await store.createTask(props.id, newTaskTitle.value.trim())
    showCreate.value = false
    newTaskTitle.value = ''
    ElMessage.success('任务已创建并冻结仓库集合')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '创建失败')
  } finally {
    saving.value = false
  }
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
.row-between {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
