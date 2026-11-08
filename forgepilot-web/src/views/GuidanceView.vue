<template>
  <div>
    <el-alert
      type="info"
      show-icon
      :closable="false"
      title="规约与文档（三层）"
      description="同标题时更具体的一层覆盖上一层；不同标题全部保留。未勾选「进入 Agent」的文档不会进入合成结果。"
    />
    <div class="toolbar">
      <el-select v-model="scope" style="width: 160px" @change="load">
        <el-option label="全部可见" value="ALL" />
        <el-option label="平台" value="PLATFORM" />
        <el-option label="公司" value="TENANT" />
        <el-option label="项目" value="PROJECT" />
      </el-select>
      <el-select
        v-if="scope === 'PROJECT' || scope === 'ALL'"
        v-model="projectId"
        clearable
        filterable
        placeholder="项目（项目层必填）"
        style="width: 220px"
        @change="load"
      >
        <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
      </el-select>
      <el-button type="primary" @click="showCreate = true">新建</el-button>
      <el-button :disabled="!projectId" @click="compose">查看合成</el-button>
      <el-button :disabled="!projectId" :loading="saving" @click="snapshot">冻结快照</el-button>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>
    <el-alert v-if="error" type="error" :closable="false" :title="error" class="block" />
    <el-card>
      <el-table :data="rows" empty-text="没有条目">
        <el-table-column prop="scope" label="层次" width="100" />
        <el-table-column prop="kind" label="种类" width="120" />
        <el-table-column prop="title" label="标题" />
        <el-table-column label="进入 Agent" width="110">
          <template #default="{ row }">{{ row.includeInAgent ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column prop="body" label="正文" show-overflow-tooltip />
      </el-table>
    </el-card>
    <el-card v-if="effective" class="block">
      <template #header>项目合成结果（将注入 Agent 的规约）</template>
      <el-table :data="effective.items">
        <el-table-column prop="scope" label="生效层次" width="120" />
        <el-table-column prop="title" label="标题" width="160" />
        <el-table-column prop="body" label="正文" />
      </el-table>
      <p v-if="lastSnapshotId">最近快照：{{ lastSnapshotId }}</p>
    </el-card>

    <el-dialog v-model="showCreate" title="新建规约/文档" width="560px">
      <el-form label-width="100px">
        <el-form-item label="层次">
          <el-select v-model="form.scope" style="width: 100%">
            <el-option label="平台" value="PLATFORM" />
            <el-option label="公司" value="TENANT" />
            <el-option label="项目" value="PROJECT" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.scope === 'PROJECT'" label="项目">
          <el-select v-model="form.projectId" style="width: 100%">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="种类">
          <el-select v-model="form.kind" style="width: 100%">
            <el-option label="规约" value="CONVENTION" />
            <el-option label="文档" value="DOCUMENT" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="正文"><el-input v-model="form.body" type="textarea" :rows="5" /></el-form-item>
        <el-form-item label="进入 Agent"><el-switch v-model="form.includeInAgent" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="create">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { apiFetch } from '@/api/http'

interface GuidanceRow {
  id: string
  scope: string
  kind: string
  title: string
  body: string
  includeInAgent: boolean
}

interface ProjectRow {
  id: string
  name: string
}

const scope = ref('ALL')
const projectId = ref('')
const projects = ref<ProjectRow[]>([])
const rows = ref<GuidanceRow[]>([])
const effective = ref<{ items: Array<{ scope: string; title: string; body: string }> } | null>(null)
const lastSnapshotId = ref('')
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const showCreate = ref(false)
const form = reactive({
  scope: 'TENANT',
  projectId: '',
  kind: 'CONVENTION',
  title: '',
  body: '',
  includeInAgent: true,
})

async function loadProjects() {
  try {
    projects.value = await apiFetch<ProjectRow[]>('/api/v1/projects')
  } catch {
    projects.value = []
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams()
    if (scope.value !== 'ALL') {
      params.set('scope', scope.value)
    }
    if (projectId.value) {
      params.set('projectId', projectId.value)
    }
    rows.value = await apiFetch<GuidanceRow[]>(`/api/v1/guidance?${params.toString()}`)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载失败'
    rows.value = []
  } finally {
    loading.value = false
  }
}

async function compose() {
  if (!projectId.value) {
    ElMessage.warning('请先选择项目')
    return
  }
  try {
    effective.value = await apiFetch(`/api/v1/projects/${projectId.value}/conventions/effective`)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '合成失败')
  }
}

async function snapshot() {
  if (!projectId.value) {
    return
  }
  saving.value = true
  try {
    const snap = await apiFetch<{ id: string; items: Array<{ scope: string; title: string; body: string }> }>(
      `/api/v1/projects/${projectId.value}/conventions/snapshots`,
      { method: 'POST' },
    )
    lastSnapshotId.value = snap.id
    effective.value = { items: snap.items }
    ElMessage.success('已冻结合成快照（尚无 Agent Session 也可保存）')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '快照失败')
  } finally {
    saving.value = false
  }
}

async function create() {
  saving.value = true
  try {
    await apiFetch('/api/v1/guidance', {
      method: 'POST',
      body: JSON.stringify({
        scope: form.scope,
        projectId: form.scope === 'PROJECT' ? form.projectId : null,
        kind: form.kind,
        title: form.title,
        body: form.body,
        includeInAgent: form.includeInAgent,
        enabled: true,
      }),
    })
    showCreate.value = false
    ElMessage.success('已创建')
    await load()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '创建失败')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await loadProjects()
  await load()
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
