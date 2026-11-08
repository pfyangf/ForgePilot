<template>
  <div>
    <el-alert
      type="info"
      show-icon
      :closable="false"
      title="这是个人工作台骨架，数据写在浏览器里，用来走通列表和分状态详情。"
      description="数据先按当前用户所属公司收口，再按项目成员关系。默认「我的」。「待我处理」只留待审核、等待人工，或会话正在等我输入的任务。打开页面本身不写操作记录。"
    />
    <div class="toolbar">
      <el-radio-group v-model="store.scope">
        <el-radio-button value="mine">我的</el-radio-button>
        <el-radio-button value="waiting">待我处理</el-radio-button>
        <el-radio-button value="member">项目内全部</el-radio-button>
      </el-radio-group>
      <el-button @click="checkHealth">检查控制面</el-button>
      <span v-if="health">{{ health }}</span>
    </div>
    <el-row :gutter="12" class="stats">
      <el-col :span="4"><el-statistic title="需求" :value="store.counts.requirements" /></el-col>
      <el-col :span="4"><el-statistic title="任务" :value="store.counts.tasks" /></el-col>
      <el-col :span="4"><el-statistic title="执行中" :value="store.counts.running" /></el-col>
      <el-col :span="4"><el-statistic title="验证中" :value="store.counts.verifying" /></el-col>
      <el-col :span="4"><el-statistic title="待审核" :value="store.counts.approval" /></el-col>
      <el-col :span="4"><el-statistic title="待处理" :value="store.counts.waitingUser" /></el-col>
    </el-row>
    <el-card>
      <template #header>需求</template>
      <el-table :data="store.visibleRequirements" empty-text="这个筛选下没有需求" @row-click="openRequirement">
        <el-table-column prop="title" label="名称" />
        <el-table-column prop="projectName" label="项目" width="120" />
        <el-table-column prop="status" label="状态" width="120" />
        <el-table-column label="任务进度" width="120">
          <template #default="{ row }">{{ progressText(row.id) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
    <el-card class="block">
      <template #header>任务</template>
      <el-table :data="store.visibleTasks" empty-text="这个筛选下没有任务" @row-click="openTask">
        <el-table-column prop="title" label="名称" />
        <el-table-column prop="projectName" label="项目" width="120" />
        <el-table-column prop="status" label="状态" width="180" />
        <el-table-column prop="agentLabel" label="Agent" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useWorkbenchStore } from '@/stores/workbench'

const store = useWorkbenchStore()
const router = useRouter()
const health = ref('')

function progressText(id: string) {
  const progress = store.progress(id)
  return progress.total === 0 ? '尚未拆任务' : `${progress.done}/${progress.total}`
}

function openRequirement(row: { id: string }) {
  router.push(`/requirements/${row.id}`)
}

function openTask(row: { id: string }) {
  router.push(`/tasks/${row.id}`)
}

async function checkHealth() {
  try {
    const response = await fetch('/api/v1/health')
    health.value = response.ok ? await response.text() : `控制面返回 ${response.status}`
  } catch {
    health.value = '控制面未启动'
  }
}
</script>

<style scoped>
.toolbar,
.stats,
.block {
  margin-top: 16px;
}
.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
}
</style>
