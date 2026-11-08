<template>
  <div>
    <el-alert
      type="success"
      show-icon
      :closable="false"
      title="个人工作台已接通控制面"
      description="列表按当前登录用户的租户与项目成员关系从服务器加载。默认先看「待我处理」。打开页面本身不写操作记录。"
    />
    <div class="toolbar">
      <el-radio-group :model-value="store.scope" @change="onScopeChange">
        <el-radio-button value="waiting">待我处理</el-radio-button>
        <el-radio-button value="mine">我的</el-radio-button>
        <el-radio-button value="member">项目内全部</el-radio-button>
      </el-radio-group>
      <el-button :loading="store.loading" @click="store.load()">刷新</el-button>
      <span v-if="store.error" class="error">{{ store.error }}</span>
    </div>
    <el-row :gutter="12" class="stats">
      <el-col :span="4"><el-statistic title="需求" :value="store.counts.requirements" /></el-col>
      <el-col :span="4"><el-statistic title="任务" :value="store.counts.tasks" /></el-col>
      <el-col :span="4"><el-statistic title="执行中" :value="store.counts.running" /></el-col>
      <el-col :span="4"><el-statistic title="验证中" :value="store.counts.verifying" /></el-col>
      <el-col :span="4"><el-statistic title="待审核" :value="store.counts.approval" /></el-col>
      <el-col :span="4"><el-statistic title="待处理" :value="store.counts.waitingUser" /></el-col>
    </el-row>
    <el-card v-loading="store.loading">
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
    <el-card class="block" v-loading="store.loading">
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
import { onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useWorkbenchStore } from '@/stores/workbench'

const store = useWorkbenchStore()
const auth = useAuthStore()
const router = useRouter()

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

function onScopeChange(value: string | number | boolean | undefined) {
  void store.load(value as 'mine' | 'member' | 'waiting')
}

onMounted(() => {
  if (auth.isAuthenticated) {
    void store.load('waiting')
  }
})

watch(
  () => auth.isAuthenticated,
  (ok) => {
    if (ok) {
      void store.load('waiting')
    }
  },
)
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
.error {
  color: #b91c1c;
}
</style>
