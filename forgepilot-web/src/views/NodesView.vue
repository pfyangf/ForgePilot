<template>
  <div>
    <el-page-header content="执行节点" />
    <el-alert
      class="block"
      type="info"
      :closable="false"
      show-icon
      title="节点在 PostgreSQL 注册并心跳。并行任务各用独立工作区与 Session 凭证；仓库 mirror 只读共享。"
    />
    <el-button class="block" :loading="loading" @click="reload">刷新</el-button>
    <el-table :data="nodes" empty-text="暂无节点" class="block">
      <el-table-column prop="nodeKey" label="节点" width="180" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column prop="capacity" label="容量" width="80" />
      <el-table-column prop="activeSessions" label="活动 Session" width="120" />
      <el-table-column prop="lastHeartbeatAt" label="最近心跳" />
      <el-table-column prop="labels" label="标签" />
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { apiFetch } from '@/api/http'

interface NodeRow {
  id: string
  nodeKey: string
  status: string
  capacity: number
  activeSessions: number
  lastHeartbeatAt?: string
  labels?: string
}

const nodes = ref<NodeRow[]>([])
const loading = ref(false)

async function reload() {
  loading.value = true
  try {
    nodes.value = await apiFetch<NodeRow[]>('/api/v1/nodes')
  } finally {
    loading.value = false
  }
}

onMounted(reload)
</script>

<style scoped>
.block {
  margin-top: 16px;
}
</style>
