<template>
  <div class="activity">
    <div class="page-header">
      <div class="page-header__titles">
        <h1 class="page-header__title">操作记录</h1>
        <p class="page-header__subtitle">只追加、不可改删 · 密钥字段打码 · Asia/Shanghai 日报</p>
      </div>
      <div class="page-header__actions">
        <el-input v-model="filters.action" placeholder="动作，如 TASK_CREATE" clearable class="filter filter--sm" />
        <el-select v-model="filters.projectId" clearable filterable placeholder="选择项目" class="filter filter--md">
          <el-option v-for="item in projects" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <el-select v-model="filters.actorUserId" clearable filterable placeholder="选择操作者" class="filter filter--md">
          <el-option
            v-for="item in users"
            :key="item.id"
            :label="item.displayName ? `${item.displayName}（${item.username}）` : item.username"
            :value="item.id"
          />
        </el-select>
        <el-button type="primary" :loading="loading" @click="load">查询</el-button>
        <el-button @click="exportJson">导出 JSON</el-button>
        <el-button @click="exportCsv">导出 CSV</el-button>
        <el-button @click="loadDaily">今日日报</el-button>
        <el-button @click="exportDailyMd">导出日报 Markdown</el-button>
      </div>
    </div>
    <el-alert v-if="error" type="error" :closable="false" :title="error" class="block" />
    <el-card>
      <el-table :data="rows" empty-text="没有记录" height="420">
        <el-table-column prop="occurredAt" label="时间" width="190" />
        <el-table-column prop="action" label="动作" width="200" />
        <el-table-column label="操作者" min-width="180">
          <template #default="{ row }">
            {{ formatActor(row) }}
          </template>
        </el-table-column>
        <el-table-column label="项目" width="200">
          <template #default="{ row }">{{ projectName(row.projectId) }}</template>
        </el-table-column>
        <el-table-column prop="result" label="结果" width="90" />
        <el-table-column prop="summary" label="摘要" />
      </el-table>
    </el-card>
    <el-card v-if="dailyMd" class="compose-gap">
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
  actorUserId: string | null
  actorUsername: string | null
  actorDisplayName: string | null
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
const projects = ref<Array<{ id: string; name: string }>>([])
const users = ref<Array<{ id: string; username: string; displayName?: string }>>([])

function projectName(id: string) {
  return projects.value.find((item) => item.id === id)?.name || id || '-'
}

function formatActor(row: ActivityRow) {
  if (row.actorDisplayName && row.actorUsername) {
    return `${row.actorDisplayName}（${row.actorUsername}）`
  }
  return row.actorDisplayName || row.actorUsername || row.actorUserId || '-'
}

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

async function loadFilters() {
  try {
    projects.value = await apiFetch('/api/v1/projects')
  } catch {
    projects.value = []
  }
  try {
    users.value = await apiFetch('/api/v1/tenant/users')
  } catch {
    users.value = []
  }
}

onMounted(() => {
  void loadFilters()
  void load()
})
</script>

<style scoped>
.page-header__actions {
  max-width: 860px;
}
.filter {
  width: 160px;
}
.filter--sm {
  width: 160px;
}
.filter--md {
  width: 200px;
}
.block {
  margin-bottom: 12px;
}
.compose-gap {
  margin-top: 12px;
}
.log {
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
