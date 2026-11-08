<template>
  <div>
    <el-alert
      type="info"
      show-icon
      :closable="false"
      title="操作记录与日报"
      description="只追加、不可改删。载荷里的密钥字段会打码。默认按 Asia/Shanghai 汇总日报。"
    />
    <div class="toolbar">
      <el-input v-model="filters.action" placeholder="动作，如 TASK_CREATE" clearable style="width: 200px" />
      <el-input v-model="filters.projectId" placeholder="项目 ID" clearable style="width: 260px" />
      <el-input v-model="filters.actorUserId" placeholder="操作者用户 ID" clearable style="width: 260px" />
      <el-button type="primary" :loading="loading" @click="load">查询</el-button>
      <el-button @click="exportJson">导出 JSON</el-button>
      <el-button @click="exportCsv">导出 CSV</el-button>
      <el-button @click="loadDaily">今日日报</el-button>
      <el-button @click="exportDailyMd">导出日报 Markdown</el-button>
    </div>
    <el-alert v-if="error" type="error" :closable="false" :title="error" />
    <el-card>
      <el-table :data="rows" empty-text="没有记录" height="420">
        <el-table-column prop="occurredAt" label="时间" width="190" />
        <el-table-column prop="action" label="动作" width="200" />
        <el-table-column prop="actorUserId" label="操作者" width="280" />
        <el-table-column prop="projectId" label="项目" width="280" />
        <el-table-column prop="result" label="结果" width="90" />
        <el-table-column prop="summary" label="摘要" />
      </el-table>
    </el-card>
    <el-card v-if="dailyMd" class="block">
      <template #header>日报 Markdown</template>
      <pre class="log">{{ dailyMd }}</pre>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiFetch, getAccessToken } from '@/api/http'

interface ActivityRow {
  id: string
  occurredAt: string
  action: string
  actorUserId: string
  projectId: string
  result: string
  summary: string
  payload: Record<string, unknown>
}

const rows = ref<ActivityRow[]>([])
const loading = ref(false)
const error = ref('')
const dailyMd = ref('')
const filters = reactive({
  action: '',
  projectId: '',
  actorUserId: '',
})

function queryString() {
  const params = new URLSearchParams()
  if (filters.action) params.set('action', filters.action)
  if (filters.projectId) params.set('projectId', filters.projectId)
  if (filters.actorUserId) params.set('actorUserId', filters.actorUserId)
  return params.toString()
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const qs = queryString()
    rows.value = await apiFetch<ActivityRow[]>(`/api/v1/activity-logs${qs ? `?${qs}` : ''}`)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '查询失败'
    rows.value = []
  } finally {
    loading.value = false
  }
}

async function exportJson() {
  const qs = queryString()
  const data = await apiFetch(`/api/v1/activity-logs/export?format=json${qs ? `&${qs}` : ''}`)
  downloadText(JSON.stringify(data, null, 2), 'activity-logs.json', 'application/json')
}

async function exportCsv() {
  const qs = queryString()
  const token = getAccessToken()
  const response = await fetch(`/api/v1/activity-logs/export?format=csv${qs ? `&${qs}` : ''}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!response.ok) {
    ElMessage.error('导出 CSV 失败')
    return
  }
  downloadText(await response.text(), 'activity-logs.csv', 'text/csv')
}

async function loadDaily() {
  try {
    const report = await apiFetch('/api/v1/reports/daily')
    dailyMd.value = JSON.stringify(report, null, 2)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载日报失败')
  }
}

async function exportDailyMd() {
  const token = getAccessToken()
  const response = await fetch('/api/v1/reports/daily.md', {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!response.ok) {
    ElMessage.error('导出日报失败')
    return
  }
  const text = await response.text()
  dailyMd.value = text
  downloadText(text, 'daily-report.md', 'text/markdown')
}

function downloadText(content: string, filename: string, type: string) {
  const blob = new Blob([content], { type })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

onMounted(() => {
  void load()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin: 16px 0;
}
.block {
  margin-top: 16px;
}
</style>
